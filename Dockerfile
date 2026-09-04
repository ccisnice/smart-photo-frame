# 飞牛 fnOS / 任意 Docker 环境通用
# 基于官方 python 镜像，自带运行环境，不依赖宿主机装 python
FROM python:3.11-slim

WORKDIR /app
COPY serve.py .
COPY index.html .
COPY media ./media

EXPOSE 8000
CMD ["python3", "serve.py"]
