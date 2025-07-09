# 基础镜像：使用OpenJDK 17的官方镜像
FROM openjdk:17-jdk-slim

# 作者信息（可选）
LABEL maintainer="2471361@stu.neu.edu.cn"

# 设置工作目录
WORKDIR /app

# 复制 judge-worker jar 包到镜像内（假设构建产物是 judge-worker.jar）
COPY target/judge-worker.jar ./judge-worker.jar

# 安装编译环境示例（gcc、python等），根据你支持的语言添加
RUN apt-get update && apt-get install -y \
    gcc \
    python3 \
    && rm -rf /var/lib/apt/lists/*

# 设置环境变量（可选，方便运行时配置）
ENV SPRING_RABBITMQ_HOST=rabbitmq
ENV SPRING_RABBITMQ_PORT=5672

# 启动judge-worker程序，监听RabbitMQ
ENTRYPOINT ["java", "-jar", "judge-worker.jar"]
