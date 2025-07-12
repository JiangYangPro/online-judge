package cn.icongyou.executor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @ClassName DockerContainerPool
 * @Description Docker容器池管理器，使用docker-java API管理容器，提高性能和可靠性
 * @Author JiangYang
 * @Date 2025/7/9 19:32
 * @Version 2.0
 **/

@Component
public class DockerContainerPool {
    private static final Logger logger = LoggerFactory.getLogger(DockerContainerPool.class);

    private static final String BASE_IMAGE = "openjdk:8-jdk-alpine";
    private static final String CONTAINER_PREFIX = "judge-pool-";
    
    @Value("${executor.pool-size:3}")
    private int poolSize;
    
    @Value("${executor.max-containers:5}")
    private int maxContainers;
    
    @Value("${docker.host:tcp://localhost:2375}")
    private String dockerHost;
    
    private final BlockingQueue<String> availableContainers = new LinkedBlockingQueue<>();
    private final ConcurrentMap<String, Long> containerUsageTime = new ConcurrentHashMap<>();
    private final AtomicInteger containerCounter = new AtomicInteger(0);
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private DockerClient dockerClient;
    
    @PostConstruct
    public void init() {
        logger.info("初始化Docker容器池，池大小: {}", poolSize);
        
        // 初始化Docker客户端
        initDockerClient();
        
        // 预热容器池
        for (int i = 0; i < poolSize; i++) {
            createContainer();
        }
        
        // 定期清理长时间未使用的容器
        scheduler.scheduleAtFixedRate(this::cleanupUnusedContainers, 5, 5, TimeUnit.MINUTES);
        
        logger.info("Docker容器池初始化完成");
    }
    
    @PreDestroy
    public void destroy() {
        logger.info("关闭Docker容器池");
        scheduler.shutdown();
        cleanupAllContainers();
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception e) {
                logger.error("关闭Docker客户端时发生错误", e);
            }
        }
    }
    
    /**
     * 初始化Docker客户端
     */
    private void initDockerClient() {
        try {
            logger.info("尝试连接到Docker主机: {}", dockerHost);
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
            
            dockerClient = DockerClientBuilder.getInstance(config).build();
            
            // 测试连接
            dockerClient.pingCmd().exec();
            logger.info("Docker客户端连接成功，主机: {}", dockerHost);
        } catch (Exception ex) {
            logger.error("Docker客户端连接失败，请确保Docker Desktop正在运行", ex);
            throw new RuntimeException("无法连接到Docker守护进程，请检查Docker Desktop是否已启动", ex);
        }
    }
    
    /**
     * 获取一个可用的容器
     */
    public String acquireContainer() throws InterruptedException {
        String containerName = availableContainers.poll(10, TimeUnit.SECONDS);
        if (containerName == null) {
            // 如果池中没有可用容器，创建新的
            containerName = createContainer();
        }
        
        if (containerName != null) {
            containerUsageTime.put(containerName, System.currentTimeMillis());
            logger.debug("获取容器: {}", containerName);
        }
        
        return containerName;
    }
    
    /**
     * 释放容器回池中
     */
    public void releaseContainer(String containerName) {
        if (containerName != null) {
            try {
                // 清理容器内容
                cleanupContainerContent(containerName);
                
                // 如果池未满，放回池中
                if (availableContainers.size() < poolSize) {
                    availableContainers.offer(containerName);
                    logger.debug("释放容器回池: {}", containerName);
                } else {
                    // 池已满，删除容器
                    deleteContainer(containerName);
                    logger.debug("删除容器: {}", containerName);
                }
            } catch (Exception e) {
                logger.error("释放容器时发生错误: {}", containerName, e);
                deleteContainer(containerName);
            }
        }
    }
    
    /**
     * 创建新容器
     */
    private String createContainer() {
        try {
            String containerName = CONTAINER_PREFIX + containerCounter.incrementAndGet();
            
            // 检查容器数量限制
            if (getTotalContainerCount() >= maxContainers) {
                logger.warn("达到最大容器数量限制: {}", maxContainers);
                return null;
            }
            
            // 使用docker-java API创建容器
            HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(256L * 1024 * 1024) // 512MB内存限制
                .withCpuCount(1L) // 1个CPU核心
                .withNetworkMode("none"); // 禁用网络以提高安全性
            
            CreateContainerResponse response = dockerClient.createContainerCmd(BASE_IMAGE)
                .withName(containerName)
                .withHostConfig(hostConfig)
                .withCmd("sh", "-c", "mkdir -p /workspace && tail -f /dev/null")
                .exec();
            
            String containerId = response.getId();
            
            // 启动容器
            dockerClient.startContainerCmd(containerId).exec();
            
            availableContainers.offer(containerName);
            logger.debug("创建容器成功: {}", containerName);
            return containerName;
            
        } catch (Exception e) {
            logger.error("创建容器时发生异常", e);
            return null;
        }
    }
    
    /**
     * 清理容器内容
     */
    private void cleanupContainerContent(String containerName) {
        try {
            // 使用docker-java API执行清理命令
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerName)
                .withCmd("sh", "-c", "rm -rf /workspace/* 2>/dev/null || true && mkdir -p /workspace")
                .exec();
            
            dockerClient.execStartCmd(execCreateCmdResponse.getId())
                .exec(new ExecStartResultCallback())
                .awaitCompletion();
            
        } catch (Exception e) {
            logger.debug("清理容器内容时发生错误: {}", containerName, e);
        }
    }
    
    /**
     * 删除容器
     */
    private void deleteContainer(String containerName) {
        try {
            // 停止容器
            dockerClient.stopContainerCmd(containerName).exec();
            
            // 删除容器
            dockerClient.removeContainerCmd(containerName).exec();
            
            containerUsageTime.remove(containerName);
            logger.debug("删除容器: {}", containerName);
        } catch (Exception e) {
            logger.error("删除容器时发生错误: {}", containerName, e);
        }
    }
    
    /**
     * 清理长时间未使用的容器
     */
    private void cleanupUnusedContainers() {
        try {
            long currentTime = System.currentTimeMillis();
            long timeout = 10 * 60 * 1000; // 10分钟超时
            
            availableContainers.removeIf(containerName -> {
                Long usageTime = containerUsageTime.get(containerName);
                if (usageTime != null && (currentTime - usageTime) > timeout) {
                    deleteContainer(containerName);
                    return true;
                }
                return false;
            });
        } catch (Exception e) {
            logger.error("清理未使用容器时发生错误", e);
        }
    }
    
    /**
     * 清理所有容器
     */
    private void cleanupAllContainers() {
        try {
            // 清理池中的容器
            String containerName;
            while ((containerName = availableContainers.poll()) != null) {
                deleteContainer(containerName);
            }
            
            // 清理所有以CONTAINER_PREFIX开头的容器
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of(CONTAINER_PREFIX + "*"))
                .exec();
            
            for (Container container : containers) {
                String[] names = container.getNames();
                if (names != null && names.length > 0) {
                    String name = names[0].substring(1); // 移除开头的'/'
                    deleteContainer(name);
                }
            }
        } catch (Exception e) {
            logger.error("清理所有容器时发生错误", e);
        }
    }
    
    /**
     * 获取总容器数量
     */
    private int getTotalContainerCount() {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of(CONTAINER_PREFIX + "*"))
                .exec();
            
            return containers.size();
        } catch (Exception e) {
            logger.error("获取容器数量时发生错误", e);
            return 0;
        }
    }
    
    /**
     * 执行命令并获取输出
     */
    public String executeCommand(String containerName, String... command) {
        try {
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerName)
                .withCmd(command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            
            dockerClient.execStartCmd(execCreateCmdResponse.getId())
                .exec(new ExecStartResultCallback(outputStream, errorStream))
                .awaitCompletion();
            
            String output = outputStream.toString();
            String error = errorStream.toString();
            
            if (!error.isEmpty()) {
                logger.warn("命令执行有错误输出: {}", error);
            }
            
            return output;
        } catch (Exception e) {
            logger.error("执行命令时发生错误: {}", String.join(" ", command), e);
            return "";
        }
    }
    
    /**
     * 获取池状态信息
     */
    public String getPoolStatus() {
        return String.format("可用容器: %d, 总容器: %d, 最大容器: %d", 
                           availableContainers.size(), getTotalContainerCount(), maxContainers);
    }
} 