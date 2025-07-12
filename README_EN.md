# Online Judge System

[中文](README.md) | English

A high-performance online judge system based on microservices architecture, supporting Java code compilation, execution, and evaluation.

## 📋 Project Overview

Online Judge is a distributed online judge system designed with microservices architecture, supporting the complete workflow of code submission, compilation, execution, and result evaluation. The system features high concurrency, high availability, and scalability, suitable for programming competitions, online education, and other scenarios.

## 🏗️ System Architecture

![System Architecture](系统结构图.png)

### Architecture Components

- **Judge Service**: Responsible for receiving code submission requests and returning evaluation results
- **Judge Worker**: Responsible for code compilation, execution, and evaluation
- **RabbitMQ**: Message queue for asynchronous communication between services
- **Redis**: Cache service for storing evaluation results and temporary data
- **Docker**: Containerized execution environment ensuring code execution security and isolation

## 🛠️ Technology Stack

### Backend Technologies
- **Java 17**: Main development language
- **Spring Boot 2.7.18**: Microservices framework
- **Spring AMQP**: RabbitMQ message queue integration
- **Spring Data Redis**: Redis cache integration
- **Docker Java API**: Docker container management
- **Maven**: Project build tool

### Middleware
- **RabbitMQ 3**: Message queue service
- **Redis 7**: Cache database
- **Docker**: Containerized execution environment

### Development Tools
- **JMeter**: Performance testing tool

## 📁 Project Structure

```
oneline-judge/
├── common-model/           # Common model module
│   ├── src/main/java/cn/icongyou/common/
│   │   ├── CodeExecutionRequest.java    # Code execution request
│   │   ├── CodeExecutionResult.java     # Code execution result
│   │   └── JudgeStatus.java             # Judge status enum
│   └── pom.xml
├── judge-service/          # Judge service module
│   ├── src/main/java/cn/icongyou/
│   │   ├── controller/
│   │   │   └── JudgeController.java     # REST API controller
│   │   ├── service/
│   │   │   └── ResultService.java       # Result service
│   │   ├── messaging/
│   │   │   └── JudgeProducer.java       # Message producer
│   │   ├── listener/
│   │   │   └── CodeExecutionResultConsumer.java  # Result consumer
│   │   ├── config/
│   │   │   ├── RabbitMQConfig.java      # RabbitMQ configuration
│   │   │   └── RedisConfig.java         # Redis configuration
│   │   └── JudgeServiceApplication.java # Service startup class
│   ├── src/main/resources/
│   │   └── application.yml              # Configuration file
│   └── pom.xml
├── judge-worker/           # Judge worker node module
│   ├── src/main/java/cn/icongyou/
│   │   ├── executor/
│   │   │   ├── JavaCodeExecutor.java    # Java code executor
│   │   │   └── DockerContainerPool.java # Docker container pool
│   │   ├── listener/
│   │   │   └── JudgeConsumer.java       # Judge task consumer
│   │   ├── messaging/
│   │   │   └── JudgeResultProducer.java # Result producer
│   │   ├── config/
│   │   │   └── RabbitMQConfig.java      # RabbitMQ configuration
│   │   └── JudgeWorkerApplication.java  # Worker node startup class
│   ├── src/main/resources/
│   │   └── application.yml              # Configuration file
│   └── pom.xml
├── docker-compose.yml      # Docker orchestration file
├── jemeter测试计划.jmx      # JMeter performance test plan
├── 系统结构图.png          # System architecture diagram
└── pom.xml                 # Parent project POM file
```

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- Docker & Docker Compose
- At least 4GB available memory

### 1. Clone the Repository

```bash
git clone https://github.com/JiangYangPro/online-judge
cd oneline-judge
```

### 2. Start Dependency Services

```bash
docker-compose up -d
```

This will start the following services:
- RabbitMQ (Port: 5672, Management UI: 15672)
- Redis (Port: 6379)

### 3. Build the Project

```bash
mvn clean compile
```

### 4. Start Services

#### Start Judge Service
```bash
cd judge-service
mvn spring-boot:run
```

#### Start Judge Worker Node
```bash
cd judge-worker
mvn spring-boot:run
```

### 5. Verify Services

After services are started, you can verify them through:

- Judge Service: http://localhost:8080
- RabbitMQ Management UI: http://localhost:15672 (Username/Password: guest/guest)

## 📖 API Documentation

### Submit Code

```http
POST /judge/submit
Content-Type: application/json

{
  "language": "java",
  "sourceCode": "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int a = sc.nextInt();\n        int b = sc.nextInt();\n        System.out.println(a + b);\n    }\n}",
  "stdin": "3 5"
}
```

Response:
```json
{
  Submission accepted: 61d60c91-9515-40c1-8a83-d324e0f1e490
}
```

### Get Judge Result

```http
GET /judge/result/{submissionId}
```

Response:
```json
{
  "submissionId": "61d60c91-9515-40c1-8a83-d324e0f1e490",
  "status": "ACCEPTED",
  "stdout": "8\n",
  "stderr": null,
  "exitCode": 0,
  "executionTimeMs": 654
}
```

## 🔧 Configuration

### Judge Service Configuration (judge-service/application.yml)

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
  
  redis:
    host: localhost
    port: 6379
    database: 0

server:
  port: 8080

app:
  result-cache-ttl: 3600        # Result cache TTL (seconds)
  max-concurrent-requests: 1000 # Maximum concurrent requests
  request-queue-size: 5000      # Request queue size
```

### Judge Worker Configuration (judge-worker/application.yml)

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

app:
  docker:
    image: openjdk:17-jdk-slim  # Execution environment image
    max-containers: 10          # Maximum number of containers
    container-timeout: 30000    # Container timeout (milliseconds)
```

## 🧪 Performance Testing

The project includes a JMeter test plan for performance testing:

```bash
jmeter -n -t jemeter测试计划.jmx -l results.jtl -e -o report
```

## 🔒 Security Features

- **Container Isolation**: Uses Docker containers for code execution, ensuring system security
- **Resource Limits**: Supports CPU time and memory usage limits
- **Timeout Control**: Prevents malicious code from infinite loops
- **Sandbox Environment**: Code execution in restricted environment

## 📊 Monitoring Metrics

The system provides the following monitoring metrics:

- Code execution success rate
- Average execution time
- Concurrent processing capacity
- Container pool utilization
- Queue backlog status

## 🤝 Contributing

1. Fork this repository
2. Create a feature branch (`git checkout -b JiangYangPro/online-judge`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin JiangYangPro/online-judge`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👥 Authors

- **JiangYang** - Initial work

## 🙏 Acknowledgments

Thanks to all developers and users who have contributed to this project.

---

If you have any questions or suggestions, please submit an [Issue](https://github.com/your-username/oneline-judge/issues).
