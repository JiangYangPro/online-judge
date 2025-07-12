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
    
    @Value("${server.port:8081}")
    private int serverPort;
    
    @Value("${instance.id:}")
    private String configuredInstanceId;
    
    private final BlockingQueue<String> availableContainers = new LinkedBlockingQueue<>();
    private final ConcurrentMap<String, Long> containerUsageTime = new ConcurrentHashMap<>();
    private final AtomicInteger containerCounter = new AtomicInteger(0);
    private final String instanceId;
    
    // 添加容器删除状态跟踪
    private final ConcurrentMap<String, Boolean> containerDeletionInProgress = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private DockerClient dockerClient;
    
    public DockerContainerPool() {
        this.instanceId = generateInstanceId();
    }
    
    @PostConstruct
    public void init() {
        logger.info("初始化Docker容器池，池大小: {}", poolSize);
        
        // 初始化Docker客户端
        initDockerClient();
        
        // 预热容器池
        for (int i = 0; i < poolSize; i++) {
            createContainerInternal();
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
        String containerName = availableContainers.poll(5, TimeUnit.SECONDS);
        if (containerName == null) {
            // 如果池中没有可用容器，尝试创建新的
            containerName = createContainerAsync();
            if (containerName == null) {
                // 如果创建失败，再次尝试从池中获取
                containerName = availableContainers.poll(5, TimeUnit.SECONDS);
            }
        }
        
        if (containerName != null) {
            containerUsageTime.put(containerName, System.currentTimeMillis());
            logger.debug("获取容器: {}", containerName);
        } else {
            logger.warn("无法获取可用容器，当前池大小: {}, 总容器数: {}", 
                       availableContainers.size(), getTotalContainerCount());
        }
        
        return containerName;
    }
    
    /**
     * 异步创建容器
     */
    private String createContainerAsync() {
        try {
            // 检查容器数量限制
            if (getTotalContainerCount() >= maxContainers) {
                logger.warn("达到最大容器数量限制: {}", maxContainers);
                return null;
            }
            
            // 使用CompletableFuture异步创建容器
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return createContainerInternal();
                } catch (Exception e) {
                    logger.error("异步创建容器失败", e);
                    return null;
                }
            });
            
            // 等待创建完成，设置超时
            return future.get(10, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            logger.error("异步创建容器时发生异常", e);
            return null;
        }
    }
    
    /**
     * 内部创建容器方法
     */
    private String createContainerInternal() {
        try {
            String containerName = CONTAINER_PREFIX + instanceId + "-" + containerCounter.incrementAndGet();
            
            // 使用docker-java API创建容器
            HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(256L * 1024 * 1024) // 256MB内存限制
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
     * 释放容器回池中
     */
    public void releaseContainer(String containerName) {
        if (containerName == null) {
            return;
        }
        
        try {
            // 异步清理容器内容，不阻塞当前线程
            CompletableFuture.runAsync(() -> {
                try {
                    cleanupContainerContent(containerName);
                } catch (Exception e) {
                    logger.debug("异步清理容器内容时发生错误: {}", containerName, e);
                }
            });
            
            // 更智能的容器管理策略
            int currentPoolSize = availableContainers.size();
            int totalContainers = getTotalContainerCount();
            
            // 优先放回池中，除非池已满或容器数量过多
            if (currentPoolSize < poolSize && totalContainers <= maxContainers) {
                availableContainers.offer(containerName);
                logger.debug("释放容器回池: {} (池大小: {}/{})", 
                           containerName, currentPoolSize + 1, poolSize);
            } 
            // 如果池已满但总容器数在合理范围内，仍然放回池中
            else if (currentPoolSize >= poolSize && totalContainers <= poolSize + 2) {
                availableContainers.offer(containerName);
                logger.debug("释放容器回池（池已满但允许溢出）: {} (池大小: {}/{})", 
                           containerName, currentPoolSize + 1, poolSize);
            }
            // 只有在容器数量明显过多时才删除
            else if (totalContainers > poolSize + 2) {
                deleteContainer(containerName);
                logger.debug("删除多余容器: {} (总容器数: {}, 池大小: {})", 
                           containerName, totalContainers, poolSize);
            } else {
                // 其他情况也放回池中
                availableContainers.offer(containerName);
                logger.debug("释放容器回池（默认策略）: {} (池大小: {}/{})", 
                           containerName, currentPoolSize + 1, poolSize);
            }
        } catch (Exception e) {
            logger.error("释放容器时发生错误: {}", containerName, e);
            // 发生错误时删除容器
            deleteContainer(containerName);
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
        if (containerName == null) {
            return;
        }
        
        // 检查是否已经在删除中
        if (containerDeletionInProgress.putIfAbsent(containerName, true) != null) {
            logger.debug("容器 {} 正在删除中，跳过重复删除", containerName);
            return;
        }
        
        try {
            // 停止容器（如果还在运行）
            try {
                dockerClient.stopContainerCmd(containerName).exec();
                logger.debug("停止容器: {}", containerName);
            } catch (Exception e) {
                // 容器可能已经停止，忽略错误
                logger.debug("停止容器时发生错误（可能已停止）: {}", containerName, e);
            }
            
            // 删除容器
            dockerClient.removeContainerCmd(containerName).exec();
            logger.debug("删除容器成功: {}", containerName);
            
        } catch (com.github.dockerjava.api.exception.ConflictException e) {
            // 容器正在被删除，这是正常的竞态条件
            logger.debug("容器 {} 正在被删除中，忽略冲突错误", containerName);
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            // 容器不存在，忽略错误
            logger.debug("容器 {} 不存在，忽略删除错误", containerName);
        } catch (Exception e) {
            logger.error("删除容器时发生错误: {}", containerName, e);
        } finally {
            // 清理状态
            containerUsageTime.remove(containerName);
            containerDeletionInProgress.remove(containerName);
        }
    }
    
    /**
     * 清理长时间未使用的容器
     */
    private void cleanupUnusedContainers() {
        try {
            long currentTime = System.currentTimeMillis();
            long timeout = 15 * 60 * 1000; // 增加到15分钟超时
            
            // 只有当池中容器数量超过池大小时才清理
            if (availableContainers.size() <= poolSize) {
                return;
            }
            
            final int maxRemoval = availableContainers.size() - poolSize; // 最多删除超出池大小的容器
            final AtomicInteger removedCount = new AtomicInteger(0);
            
            availableContainers.removeIf(containerName -> {
                if (removedCount.get() >= maxRemoval) {
                    return false; // 停止删除
                }
                
                Long usageTime = containerUsageTime.get(containerName);
                if (usageTime != null && (currentTime - usageTime) > timeout) {
                    deleteContainer(containerName);
                    removedCount.incrementAndGet();
                    return true;
                }
                return false;
            });
            
            if (removedCount.get() > 0) {
                logger.info("清理了 {} 个长时间未使用的容器", removedCount.get());
            }
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
            
            // 只清理属于当前实例的容器
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of(CONTAINER_PREFIX + instanceId + "*"))
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
     * 获取可用容器数量（公共方法）
     */
    public int getAvailableContainerCount() {
        return availableContainers.size();
    }
    
    /**
     * 获取总容器数量（公共方法）
     */
    public int getTotalContainerCount() {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of(CONTAINER_PREFIX + instanceId + "*"))
                .exec();
            
            return containers.size();
        } catch (Exception e) {
            logger.error("获取容器数量时发生错误", e);
            return 0;
        }
    }
    

    
    /**
     * 异步执行命令并获取输出
     */
    public CompletableFuture<String> executeCommandAsync(String containerName, String... command) {
        return CompletableFuture.supplyAsync(() -> {
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
                logger.error("异步执行命令时发生错误: {}", String.join(" ", command), e);
                return "";
            }
        });
    }
    
    /**
     * 执行命令并获取输出（同步版本，保持向后兼容）
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
        int availableCount = availableContainers.size();
        int totalCount = getTotalContainerCount();
        int inUseCount = totalCount - availableCount;
        
        return String.format("容器池状态 - 可用: %d, 使用中: %d, 总数: %d, 最大限制: %d", 
                           availableCount, inUseCount, totalCount, maxContainers);
    }
    
    /**
     * 获取详细的池状态信息
     */
    public String getDetailedPoolStatus() {
        int availableCount = availableContainers.size();
        int totalCount = getTotalContainerCount();
        int inUseCount = totalCount - availableCount;
        
        StringBuilder status = new StringBuilder();
        status.append("=== 容器池详细状态 ===\n");
        status.append(String.format("可用容器数: %d\n", availableCount));
        status.append(String.format("使用中容器数: %d\n", inUseCount));
        status.append(String.format("总容器数: %d\n", totalCount));
        status.append(String.format("最大容器限制: %d\n", maxContainers));
        status.append(String.format("初始池大小: %d\n", poolSize));
        status.append(String.format("容器利用率: %.2f%%\n", 
                                  totalCount > 0 ? (double)inUseCount / totalCount * 100 : 0));
        
        // 显示最近使用的容器
        status.append("\n最近使用的容器:\n");
        containerUsageTime.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
            .limit(5)
            .forEach(entry -> {
                long timeSinceUse = System.currentTimeMillis() - entry.getValue();
                status.append(String.format("  %s (%.1f秒前使用)\n", 
                                          entry.getKey(), timeSinceUse / 1000.0));
            });
        
        return status.toString();
    }
    
    /**
     * 获取Docker客户端实例（用于监控）
     */
    public DockerClient getDockerClient() {
        return dockerClient;
    }
    
    /**
     * 生成实例ID，确保多个worker实例的容器名称不重复
     */
    private String generateInstanceId() {
        // 如果配置了实例ID，使用配置的ID
        if (configuredInstanceId != null && !configuredInstanceId.trim().isEmpty()) {
            return configuredInstanceId.trim();
        }
        // 否则自动生成实例ID
        return "worker-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
} 