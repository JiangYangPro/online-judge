# Oneline Judge

Oneline Judge 是一个分布式在线判题系统，支持多语言代码的自动编译与运行，适用于在线编程竞赛、OJ 平台等场景。

## 项目结构

```
oneline-judge/
├── common-model/      # 通用数据模型模块
├── docker/            # Docker 相关配置与启动文件
├── judge-service/     # 判题服务（后端主服务，负责任务分发与结果收集）
├── judge-worker/      # 判题工作节点（负责具体代码执行与判题）
├── pom.xml            # Maven 父工程配置
└── .gitignore         # Git 忽略文件
```

## 功能特性

- 支持分布式判题，任务自动分发
- 支持多语言代码编译与运行（可扩展）
- 基于 RabbitMQ 实现服务间消息通信
- Docker 容器隔离，安全执行用户代码
- 代码与判题结果解耦，易于扩展

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/你的用户名/oneline-judge.git
cd oneline-judge
```

### 2. 环境准备

- JDK 8 及以上
- Maven 3.6+
- Docker & Docker Compose
- RabbitMQ（可通过 docker-compose 一键启动）

### 3. 启动服务

在项目根目录下执行：

```bash
cd docker
docker-compose up -d
```

### 4. 构建与运行

分别进入 `common-model`、`judge-service`、`judge-worker` 目录，执行：

```bash
mvn clean package
```

然后根据需要启动各自的服务。

## 主要模块说明

- **common-model**  
  定义了判题请求、判题结果等通用数据结构。

- **judge-service**  
  判题主服务，负责接收判题请求、分发任务、收集结果。

- **judge-worker**  
  判题工作节点，监听判题任务，拉取代码并在 Docker 容器中安全执行，返回结果。

- **docker**  
  包含 Docker Compose 配置文件，可一键启动 RabbitMQ、判题服务等。

## 配置说明

各模块下的 `src/main/resources/application.yml` 可根据实际环境修改 RabbitMQ、数据库等配置。

## 贡献指南

欢迎提交 Issue 和 PR！如有建议或问题请在 GitHub 上反馈。

## License

本项目采用 MIT License，详见 [LICENSE](./LICENSE)。