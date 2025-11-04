# 使用官方基础镜像
FROM ubuntu:latest

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive \
    TZ=Etc/UTC \
    POSTGRESQL_HOST=postgres \
    POSTGRESQL_PORT=5432 \
    POSTGRESQL_DB=thingsboard \
    POSTGRESQL_USER=admin \
    POSTGRESQL_PASSWORD=wharl2024.com

# 安装依赖项
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    openjdk-11-jdk \
    wget \
    net-tools \
    postgresql-client && \
    rm -rf /var/lib/apt/lists/*

# 复制 ThingsBoard 安装包
COPY application/target/thingsboard.deb /tmp/thingsboard.deb

# 安装 ThingsBoard
RUN dpkg -i /tmp/thingsboard.deb || apt-get install -f -y && \
    rm /tmp/thingsboard.deb

# 复制自定义配置文件
COPY thingsboard.yml /etc/thingsboard/conf/thingsboard.yml

# 暴露端口
EXPOSE 8080 1883 5683/udp 5685/udp

# 设置启动脚本
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 设置入口点
ENTRYPOINT ["/entrypoint.sh"]