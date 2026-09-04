# 在飞牛 fnOS 上部署滚动相框（Docker 版）

把旧 iPad 当成数字相框，照片存在家里的飞牛 NAS 上，iPad 连家里 WiFi 打开网页即可。**NAS 常开，比电脑更省事。**

## 前提
- 飞牛 fnOS 已联网，和 iPad 在同一局域网
- 建议给 NAS 设**固定 IP**（飞牛「网络」设置，或在路由器里绑定 MAC），这样 iPad 访问地址不会变
- 飞牛「应用中心」里装好 **Docker**

## 步骤

### 1. 上传文件到 NAS
在飞牛「文件管理」里新建一个共享文件夹，比如 `photo-frame`，把下面 3 个文件传进去：
- `Dockerfile`
- `docker-compose.yml`
- `serve.py`

（不用传 `media/`，容器运行时飞牛会自动建，或你手动建一个同名文件夹放照片）

### 2. 启动容器
- 打开飞牛 **Docker** →「项目」→ 导入 / 新建 compose 项目，指向刚才的 `docker-compose.yml`
- 或 SSH 终端进入该目录执行：`docker compose up -d`
- 端口 `8000`，重启策略已设 `unless-stopped`（NAS 重启后自动起来）

### 3. 放照片
把照片 / 视频丢进 `photo-frame/media/` 文件夹即可。两种方式：
- 飞牛「文件管理」直接上传
- **手机拍完即同步**：飞牛手机 App → 上传到这个 `media` 文件夹（比 AirDrop 到电脑再拷更省手）

### 4. iPad 观看
iPad 用 Safari 打开：`http://<飞牛局域网IP>:8000`
点「沉浸」按钮，立起来当相框。新增/删除照片后，**下拉刷新页面**就更新。

## 备选：SSH 终端直跑（不装 Docker）
飞牛已有官方 SSH 终端。进入 `photo-frame` 目录后先确认有 python：
```
python3 --version
```
若有，直接 `python3 serve.py` 即可（无需 Docker）。若提示没有 python3，就用上面的 Docker 方案。

## 注意事项
- 这是**纯局域网服务**，不要做公网端口转发，避免照片外泄
- 想让 iPad 不输端口，可在飞牛 Docker 把宿主机端口改成 `80`（需确认 80 未被占用）
- 展示时 iPad 仍要去「设置 → 自动锁定 → 永不」防止熄屏
