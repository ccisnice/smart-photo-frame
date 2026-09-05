#!/usr/bin/env python3
"""
智能滚动相框 · 局域网核心服务 (带手机端无线直传与自动压缩支持)
--------------------------------------------------------------
特点：
  - 纯 Python 3 标准库，无需 pip 安装任何依赖
  - 支持手机扫码无线上传（支持批量上传与前端 2K 智能压缩）
  - 自动局域网 IP 发现与多终端同步
  - 兼容 PWA 独立安装到平板主屏幕
"""
import os, sys, json, socket, urllib.parse, time, mimetypes
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

PORT = 8000
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MEDIA_DIR = os.path.join(BASE_DIR, "media")

IMG_EXT = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".bmp"}
VID_EXT = {".mp4", ".mov", ".m4v", ".webm"}
ALLOWED_EXT = IMG_EXT | VID_EXT

def scan_media():
    items = []
    if not os.path.isdir(MEDIA_DIR):
        os.makedirs(MEDIA_DIR, exist_ok=True)
        return items
    # 按修改时间倒序排列，新上传的照片排在最前面
    files = []
    for name in os.listdir(MEDIA_DIR):
        if name.startswith("."):
            continue
        ext = os.path.splitext(name)[1].lower()
        if ext in ALLOWED_EXT:
            fp = os.path.join(MEDIA_DIR, name)
            try:
                mtime = os.path.getmtime(fp)
                files.append((mtime, name, ext))
            except OSError:
                pass
    
    files.sort(key=lambda x: x[0], reverse=True)
    for mtime, name, ext in files:
        mtype = "image" if ext in IMG_EXT else "video"
        items.append({
            "name": name,
            "type": mtype,
            "url": "/media/" + urllib.parse.quote(name),
            "mtime": int(mtime)
        })
    return items

def local_ip():
    # 优先检测物理局域网接口 (192.168.*, 10.*, 172.16~31.*)，排除 VPN / TUN 虚拟网卡 (如 198.18.*)
    try:
        import subprocess, re
        out = subprocess.check_output(["ifconfig"], text=True)
        ips = re.findall(r'inet\s+(\d+\.\d+\.\d+\.\d+)', out)
        for ip in ips:
            if ip.startswith("192.168.") or ip.startswith("10."):
                return ip
            if ip.startswith("172."):
                parts = ip.split(".")
                if 16 <= int(parts[1]) <= 31:
                    return ip
    except Exception:
        pass

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if not ip.startswith("198.18.") and not ip.startswith("127."):
            return ip
    except Exception:
        pass
    return "127.0.0.1"

class FrameHandler(SimpleHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def __init__(self, *a, **kw):
        super().__init__(*a, directory=BASE_DIR, **kw)

    def send_json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Filename, Range")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def end_headers(self):
        if not any(h.startswith(b"Access-Control-Allow-Origin:") for h in getattr(self, "_headers_buffer", [])):
            self.send_header("Access-Control-Allow-Origin", "*")
        if not any(h.startswith(b"Cache-Control:") for h in getattr(self, "_headers_buffer", [])):
            self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Filename, Range")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/api/media":
            return self.send_json(scan_media())

        if path == "/api/info":
            ip = local_ip()
            return self.send_json({
                "ip": ip,
                "port": PORT,
                "upload_url": f"http://{ip}:{PORT}/upload",
                "tablet_url": f"http://{ip}:{PORT}/",
                "media_count": len(scan_media())
            })

        if path == "/upload":
            self.path = "/upload.html"
            return super().do_GET()

        if path == "/manifest.json":
            self.send_response(200)
            self.send_header("Content-Type", "application/manifest+json; charset=utf-8")
            fp = os.path.join(BASE_DIR, "manifest.json")
            if os.path.exists(fp):
                with open(fp, "rb") as f:
                    content = f.read()
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)
                return

        # 核心增强：为视频和媒体文件实现 HTTP 206 Range 分段流式传输（iOS Safari 必需）
        if path.startswith("/media/"):
            filename = urllib.parse.unquote(path[7:])
            filepath = os.path.join(MEDIA_DIR, os.path.basename(filename))
            if not os.path.exists(filepath):
                self.send_error(404, "File Not Found")
                return

            total_size = os.path.getsize(filepath)
            content_type, _ = mimetypes.guess_type(filepath)
            if not content_type:
                content_type = "video/quicktime" if filepath.lower().endswith(".mov") else "application/octet-stream"

            range_header = self.headers.get("Range")
            if range_header and range_header.startswith("bytes="):
                try:
                    ranges = range_header[6:].split("-")
                    start = int(ranges[0]) if ranges[0] else 0
                    end = int(ranges[1]) if len(ranges) > 1 and ranges[1] else total_size - 1
                    if start >= total_size or end >= total_size or start > end:
                        self.send_response(416)
                        self.send_header("Content-Range", f"bytes */{total_size}")
                        self.end_headers()
                        return

                    chunk_len = end - start + 1
                    self.send_response(206)
                    self.send_header("Content-Type", content_type)
                    self.send_header("Content-Range", f"bytes {start}-{end}/{total_size}")
                    self.send_header("Content-Length", str(chunk_len))
                    self.send_header("Accept-Ranges", "bytes")
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.end_headers()

                    with open(filepath, "rb") as f:
                        f.seek(start)
                        sent = 0
                        chunk_buf = 64 * 1024
                        while sent < chunk_len:
                            buf = f.read(min(chunk_buf, chunk_len - sent))
                            if not buf:
                                break
                            self.wfile.write(buf)
                            sent += len(buf)
                    self.wfile.flush()
                    return
                except (ConnectionResetError, BrokenPipeError):
                    return
                except Exception as e:
                    return

            # 无 Range 时的正常请求，但声明 Accept-Ranges 支持分段
            try:
                self.send_response(200)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(total_size))
                self.send_header("Accept-Ranges", "bytes")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Cache-Control", "no-cache")
                self.end_headers()
                with open(filepath, "rb") as f:
                    chunk_buf = 64 * 1024
                    while True:
                        buf = f.read(chunk_buf)
                        if not buf:
                            break
                        self.wfile.write(buf)
                self.wfile.flush()
            except (ConnectionResetError, BrokenPipeError):
                pass
            return

        return super().do_GET()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/api/delete":
            try:
                query_params = urllib.parse.parse_qs(parsed.query)
                name = query_params.get("name", [None])[0]
                if not name:
                    return self.send_json({"error": "缺少文件名"}, status=400)
                name = os.path.basename(urllib.parse.unquote(name))
                target_path = os.path.join(MEDIA_DIR, name)
                if os.path.exists(target_path):
                    os.remove(target_path)
                    print(f"[-] 成功删除照片: {name}")
                    return self.send_json({"success": True, "name": name})
                return self.send_json({"error": "文件不存在"}, status=404)
            except Exception as e:
                return self.send_json({"error": str(e)}, status=500)

        if path == "/api/upload":
            try:
                # 获取文件名（支持 URL query 参数或 Header）
                query_params = urllib.parse.parse_qs(parsed.query)
                raw_filename = query_params.get("filename", [None])[0]
                if not raw_filename:
                    raw_filename = self.headers.get("X-Filename")
                if raw_filename:
                    filename = urllib.parse.unquote(raw_filename)
                else:
                    filename = f"photo_{int(time.time()*1000)}.jpg"

                filename = os.path.basename(filename)
                ext = os.path.splitext(filename)[1].lower()
                if ext not in ALLOWED_EXT:
                    filename += ".jpg"
                    ext = ".jpg"

                # 防重名覆盖：如果存在同名文件，自动追加时间戳
                target_path = os.path.join(MEDIA_DIR, filename)
                if os.path.exists(target_path):
                    stem = os.path.splitext(filename)[0]
                    filename = f"{stem}_{int(time.time())}{ext}"
                    target_path = os.path.join(MEDIA_DIR, filename)

                content_len = int(self.headers.get("Content-Length", 0))
                if content_len <= 0:
                    return self.send_json({"error": "上传内容为空"}, status=400)

                # 读取二进制流写入隐藏 .part 临时文件，100% 传输完毕后原子重命名，防止相框轮播抢播未传完的残片
                os.makedirs(MEDIA_DIR, exist_ok=True)
                temp_path = os.path.join(MEDIA_DIR, f".{filename}.part")
                try:
                    with open(temp_path, "wb") as f:
                        remaining = content_len
                        chunk_size = 64 * 1024
                        while remaining > 0:
                            read_bytes = self.rfile.read(min(remaining, chunk_size))
                            if not read_bytes:
                                break
                            f.write(read_bytes)
                            remaining -= len(read_bytes)

                    os.replace(temp_path, target_path)
                except Exception as write_err:
                    if os.path.exists(temp_path):
                        os.remove(temp_path)
                    raise write_err

                print(f"[+] 成功接收新照片/视频: {filename} ({content_len / 1024:.1f} KB)")
                return self.send_json({
                    "success": True,
                    "name": filename,
                    "url": "/media/" + urllib.parse.quote(filename),
                    "size": content_len
                })

            except Exception as e:
                return self.send_json({"error": str(e)}, status=500)

        self.send_error(404, "Not Found")

    def log_message(self, fmt, *args):
        # 保持控制台清晰，仅在上传或报错时打印
        if "POST /api/upload" in str(args):
            pass
        elif args and str(args[1]) in ("404", "500"):
            print(f"[!] 访问警告: {args}")

if __name__ == "__main__":
    os.makedirs(MEDIA_DIR, exist_ok=True)
    ip = local_ip()
    print("=" * 56)
    print(" 智能滚动相框服务已启动")
    print(f" 📺 平板展示端:  http://{ip}:{PORT}/")
    print(f" 📱 手机上传端:  http://{ip}:{PORT}/upload")
    print(" 照片保存目录:  " + MEDIA_DIR)
    print("=" * 56)
    try:
        ThreadingHTTPServer(("0.0.0.0", PORT), FrameHandler).serve_forever()
    except KeyboardInterrupt:
        print("\n已安全停止")
