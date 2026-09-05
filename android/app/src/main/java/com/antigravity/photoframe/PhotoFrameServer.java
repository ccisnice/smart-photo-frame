package com.antigravity.photoframe;

import android.content.Context;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhotoFrameServer extends NanoHTTPD {
    private static final String TAG = "PhotoFrameServer";
    public static final int PORT = 8000;
    private final Context context;
    private final File mediaDir;

    public PhotoFrameServer(Context context) {
        super(PORT);
        this.context = context;
        this.mediaDir = new File(context.getFilesDir(), "media");
        if (!this.mediaDir.exists()) {
            this.mediaDir.mkdirs();
        }
    }

    public File getMediaDir() {
        return mediaDir;
    }

    public static class NetInfo {
        public String ip = "127.0.0.1";
        public String iface = "none";
        public boolean isWifi = false;
        public String warning = "";
    }

    public static NetInfo getNetworkInfo() {
        NetInfo info = new NetInfo();
        String wifiIp = null;
        String wifiIface = null;
        String hotspotIp = null;
        String otherLanIp = null;
        String cellularIp = null;

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                String name = iface.getName().toLowerCase();

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip == null || ip.isEmpty() || ip.equals("127.0.0.1")) continue;

                        Log.d(TAG, "扫描到网卡: " + name + " -> " + ip);

                        // 1. 优先抓取 Wi-Fi (wlan) 或 有线以太网 (eth)
                        if (name.startsWith("wlan") || name.startsWith("eth")) {
                            if (wifiIp == null) {
                                wifiIp = ip;
                                wifiIface = name;
                            }
                        }
                        // 2. 抓取热点网卡 (ap, softap)
                        else if (name.startsWith("ap") || name.startsWith("softap") || name.contains("hotspot")) {
                            if (hotspotIp == null) {
                                hotspotIp = ip;
                            }
                        }
                        // 3. 记录移动蜂窝网络或虚拟网卡 (rmnet, ccmni, tun, ppp)
                        else if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") ||
                                 name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("tap")) {
                            if (cellularIp == null) {
                                cellularIp = ip;
                            }
                        }
                        // 4. 其他局域网 IP
                        else if (ip.startsWith("192.168.") || ip.startsWith("172.") || ip.startsWith("10.")) {
                            if (otherLanIp == null) {
                                otherLanIp = ip;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取局域网 IP 异常", e);
        }

        // 决策最终输出 IP：坚决优先使用 Wi-Fi！
        if (wifiIp != null) {
            info.ip = wifiIp;
            info.iface = wifiIface != null ? wifiIface : "wlan0";
            info.isWifi = true;
        } else if (hotspotIp != null) {
            info.ip = hotspotIp;
            info.iface = "hotspot";
            info.isWifi = true;
        } else if (otherLanIp != null) {
            info.ip = otherLanIp;
            info.iface = "lan";
            info.isWifi = true;
        } else if (cellularIp != null) {
            info.ip = cellularIp;
            info.iface = "cellular";
            info.isWifi = false;
            info.warning = "平板当前使用的是蜂窝移动数据或开启了VPN，手机无法通过Wi-Fi连接，请将平板连上家庭Wi-Fi！";
        } else {
            info.ip = "127.0.0.1";
            info.iface = "loopback";
            info.isWifi = false;
            info.warning = "未检测到可用Wi-Fi网络，请将平板连接家庭Wi-Fi！";
        }

        Log.i(TAG, "最终选定服务 IP: " + info.ip + " (" + info.iface + "), isWifi: " + info.isWifi);
        return info;
    }

    public static String getLocalIpAddress() {
        return getNetworkInfo().ip;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        Log.d(TAG, "收到请求: " + method + " " + uri);

        // 处理跨域预检
        if (Method.OPTIONS.equals(method)) {
            Response resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
            addCorsHeaders(resp);
            return resp;
        }

        try {
            if (uri.equals("/api/device_info") || uri.equals("/api/info")) {
                NetInfo net = getNetworkInfo();
                String uploadUrl = "http://" + net.ip + ":" + PORT + "/upload";
                String json = "{"
                        + "\"ip\":\"" + net.ip + "\","
                        + "\"port\":" + PORT + ","
                        + "\"upload_url\":\"" + uploadUrl + "\","
                        + "\"iface\":\"" + net.iface + "\","
                        + "\"is_wifi\":" + net.isWifi + ","
                        + "\"warning\":\"" + escapeJson(net.warning) + "\","
                        + "\"isNative\":true"
                        + "}";
                Response resp = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json);
                addCorsHeaders(resp);
                return resp;
            }

            if (uri.equals("/api/media")) {
                return handleGetMediaList();
            }

            if (uri.equals("/api/delete") && Method.POST.equals(method)) {
                return handleDeleteMedia(session);
            }

            if ((uri.equals("/api/upload") || uri.equals("/upload")) && Method.POST.equals(method)) {
                return handleUpload(session);
            }

            if (uri.startsWith("/media/")) {
                return handleServeMedia(session, uri.substring("/media/".length()));
            }

            // 静态网页托管（手机扫码上传页面）
            if (uri.equals("/") || uri.equals("/upload") || uri.equals("/upload.html")) {
                return serveAsset("public/upload.html", "text/html; charset=utf-8");
            }
            if (uri.equals("/qrcode.min.js")) {
                return serveAsset("public/qrcode.min.js", "application/javascript");
            }
            if (uri.equals("/icon.png")) {
                return serveAsset("public/icon.png", "image/png");
            }
            if (uri.equals("/icon-192.png")) {
                return serveAsset("public/icon-192.png", "image/png");
            }
            if (uri.equals("/manifest.json")) {
                return serveAsset("public/manifest.json", "application/json");
            }

        } catch (Exception e) {
            Log.e(TAG, "处理请求异常: " + uri, e);
            Response errResp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Server Error: " + e.getMessage());
            addCorsHeaders(errResp);
            return errResp;
        }

        Response notFound = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found: " + uri);
        addCorsHeaders(notFound);
        return notFound;
    }

    private Response handleGetMediaList() {
        File[] files = mediaDir.listFiles();
        List<Map<String, Object>> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !f.getName().startsWith(".") && !f.getName().endsWith(".part") && !f.getName().endsWith(".tmp")) {
                    String name = f.getName().toLowerCase();
                    boolean isVideo = name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".m4v");
                    boolean isImg = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif");
                    if (isVideo || isImg) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("name", f.getName());
                        item.put("url", "/media/" + f.getName());
                        item.put("type", isVideo ? "video" : "photo");
                        item.put("mtime", f.lastModified() / 1000.0);
                        list.add(item);
                    }
                }
            }
        }

        // 按修改时间降序排序（最新上传的在前）
        Collections.sort(list, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                Double m1 = (Double) o1.get("mtime");
                Double m2 = (Double) o2.get("mtime");
                return m2.compareTo(m1);
            }
        });

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> m = list.get(i);
            sb.append("{")
              .append("\"name\":\"").append(escapeJson((String) m.get("name"))).append("\",")
              .append("\"url\":\"").append(m.get("url")).append("\",")
              .append("\"type\":\"").append(m.get("type")).append("\",")
              .append("\"mtime\":").append(m.get("mtime"))
              .append("}");
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");

        Response resp = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", sb.toString());
        addCorsHeaders(resp);
        resp.addHeader("Cache-Control", "no-cache");
        return resp;
    }

    private Response handleUpload(IHTTPSession session) {
        String contentType = session.getHeaders().get("content-type");
        if (contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
            return handleMultipartUpload(session);
        } else {
            return handleRawBinaryUpload(session);
        }
    }

    private Response handleRawBinaryUpload(IHTTPSession session) {
        File tempPartFile = null;
        try {
            String filename = null;
            List<String> fnList = session.getParameters().get("filename");
            if (fnList != null && !fnList.isEmpty()) {
                filename = fnList.get(0);
            }
            if (filename == null || filename.trim().isEmpty()) {
                filename = session.getHeaders().get("x-filename");
            }
            if (filename != null && !filename.trim().isEmpty()) {
                filename = java.net.URLDecoder.decode(filename, "UTF-8");
                filename = new File(filename).getName();
            } else {
                filename = "photo_" + System.currentTimeMillis() + ".jpg";
            }

            File target = new File(mediaDir, filename);
            if (target.exists()) {
                int dot = filename.lastIndexOf('.');
                String base = dot > 0 ? filename.substring(0, dot) : filename;
                String ext = dot > 0 ? filename.substring(dot) : ".jpg";
                filename = base + "_" + (System.currentTimeMillis() % 100000) + ext;
                target = new File(mediaDir, filename);
            }

            // 关键修复：使用点号开头的隐藏 .part 临时文件接收，防止相框轮播在视频尚未传输完时抢先播放导致半截中断
            tempPartFile = new File(mediaDir, "." + filename + ".part");

            String lenStr = session.getHeaders().get("content-length");
            long contentLength = 0;
            if (lenStr != null) {
                try { contentLength = Long.parseLong(lenStr); } catch (Exception ignored) {}
            }

            InputStream is = session.getInputStream();
            try (FileOutputStream fos = new FileOutputStream(tempPartFile)) {
                byte[] buf = new byte[64 * 1024];
                long bytesReadTotal = 0;
                while (bytesReadTotal < contentLength) {
                    int toRead = (int) Math.min(buf.length, contentLength - bytesReadTotal);
                    int read = is.read(buf, 0, toRead);
                    if (read == -1) break;
                    fos.write(buf, 0, read);
                    bytesReadTotal += read;
                }
                fos.flush();
            }

            // 只有当 100% 字节完全接收并刷盘后，才原子重命名为正式目标文件
            if (!tempPartFile.renameTo(target)) {
                copyFile(tempPartFile, target);
                tempPartFile.delete();
            }

            Log.i(TAG, "成功接收手机上传二进制流完整文件: " + filename + " (大小: " + target.length() + " 字节)");

            String json = "{\"ok\":true,\"success\":true,\"name\":\"" + escapeJson(filename) + "\"}";
            Response resp = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json);
            addCorsHeaders(resp);
            return resp;
        } catch (Exception e) {
            Log.e(TAG, "处理二进制流上传异常", e);
            if (tempPartFile != null && tempPartFile.exists()) {
                tempPartFile.delete();
            }
            Response resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            addCorsHeaders(resp);
            return resp;
        }
    }

    private Response handleMultipartUpload(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);

            int count = 0;
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String fieldName = entry.getKey();
                String tempFilePath = entry.getValue();
                if (tempFilePath == null || tempFilePath.isEmpty()) continue;

                String filename = "photo_" + System.currentTimeMillis() + "_" + (count + 1) + ".jpg";
                List<String> values = session.getParameters().get(fieldName);
                if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).trim().isEmpty()) {
                    filename = new File(values.get(0).trim()).getName();
                }

                File dest = new File(mediaDir, filename);
                File src = new File(tempFilePath);
                if (src.exists()) {
                    if (!src.renameTo(dest)) {
                        copyFile(src, dest);
                        src.delete();
                    }
                    count++;
                    Log.i(TAG, "成功接收手机上传 Multipart 文件: " + filename + " (大小: " + dest.length() + " 字节)");
                }
            }

            String json = "{\"ok\":true,\"success\":true,\"count\":" + count + "}";
            Response resp = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json);
            addCorsHeaders(resp);
            return resp;
        } catch (Exception e) {
            Log.e(TAG, "处理 Multipart 上传异常", e);
            Response resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            addCorsHeaders(resp);
            return resp;
        }
    }

    private Response handleDeleteMedia(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);

        String name = session.getParms().get("name");
        boolean ok = false;
        if (name != null) {
            File target = new File(mediaDir, new File(name).getName());
            if (target.exists() && target.isFile()) {
                ok = target.delete();
            }
        }

        Response resp = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", "{\"ok\":" + ok + "}");
        addCorsHeaders(resp);
        return resp;
    }

    private Response handleServeMedia(IHTTPSession session, String filename) {
        try {
            String decoded = java.net.URLDecoder.decode(filename, "UTF-8");
            File file = new File(mediaDir, decoded);
            if (!file.exists() || !file.isFile()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File Not Found");
            }

            String mime = getMimeType(decoded);
            long fileLength = file.length();
            String rangeHeader = session.getHeaders().get("range");

            // 支持 HTTP 206 Partial Content 分段流式传输（对 iPad / Android 视频播放极其关键）
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String rangeVal = rangeHeader.substring("bytes=".length()).trim();
                long start = 0;
                long end = fileLength - 1;
                int dashPos = rangeVal.indexOf('-');
                if (dashPos != -1) {
                    String startStr = rangeVal.substring(0, dashPos).trim();
                    String endStr = rangeVal.substring(dashPos + 1).trim();
                    if (!startStr.isEmpty()) start = Long.parseLong(startStr);
                    if (!endStr.isEmpty()) end = Long.parseLong(endStr);
                }

                if (start >= fileLength) {
                    Response res = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "");
                    res.addHeader("Content-Range", "bytes */" + fileLength);
                    return res;
                }

                if (end >= fileLength) end = fileLength - 1;
                long contentLength = end - start + 1;

                FileInputStream fis = new FileInputStream(file);
                fis.skip(start);

                Response res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, fis, contentLength);
                res.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                res.addHeader("Accept-Ranges", "bytes");
                addCorsHeaders(res);
                return res;
            }

            FileInputStream fis = new FileInputStream(file);
            Response res = newFixedLengthResponse(Response.Status.OK, mime, fis, fileLength);
            res.addHeader("Accept-Ranges", "bytes");
            res.addHeader("Cache-Control", "no-cache");
            addCorsHeaders(res);
            return res;
        } catch (Exception e) {
            Log.e(TAG, "流式传输异常: " + filename, e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    private Response serveAsset(String assetPath, String mime) {
        try {
            InputStream is = context.getAssets().open(assetPath);
            Response res = newChunkedResponse(Response.Status.OK, mime, is);
            addCorsHeaders(res);
            res.addHeader("Cache-Control", "no-cache");
            return res;
        } catch (Exception e) {
            Log.e(TAG, "静态文件未找到: " + assetPath, e);
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Asset not found: " + assetPath);
        }
    }

    private void addCorsHeaders(Response resp) {
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD");
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Range, Authorization, Origin, X-Requested-With, X-Filename, *");
    }

    private String getMimeType(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".mp4") || name.endsWith(".m4v")) return "video/mp4";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".webm")) return "video/webm";
        return "application/octet-stream";
    }

    private void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
