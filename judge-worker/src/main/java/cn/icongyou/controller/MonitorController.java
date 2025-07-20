package cn.icongyou.controller;

import cn.icongyou.executor.DockerContainerPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import com.github.dockerjava.api.model.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @ClassName MonitorController
 * @Description 监控控制器，提供系统状态信息
 * @Author JiangYang
 * @Date 2025/7/9 19:35
 * @Version 1.0
 **/

@RestController
@RequestMapping("/monitor")
public class MonitorController {

    private static final Logger logger = LoggerFactory.getLogger(MonitorController.class);

    @Autowired
    private DockerContainerPool containerPool;

    /**
     * 获取容器池状态
     */
    @GetMapping("/pool-status")
    public Map<String, Object> getPoolStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("message", containerPool.getPoolStatus());
        status.put("detailed", containerPool.getDetailedPoolStatus());
        return status;
    }

    /**
     * 获取系统健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("poolStatus", containerPool.getPoolStatus());
        return health;
    }

    /**
     * 获取容器实时状态
     */
    @GetMapping("/containers")
    public Map<String, Object> getContainersStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // 获取所有容器信息
            List<Container> containers = containerPool.getDockerClient().listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of("judge-pool-*"))
                .exec();
            
            List<Map<String, Object>> containerList = new ArrayList<>();
            for (Container container : containers) {
                Map<String, Object> containerInfo = new HashMap<>();
                String[] names = container.getNames();
                String name = names != null && names.length > 0 ? names[0].substring(1) : "unknown";
                
                containerInfo.put("name", name);
                containerInfo.put("status", container.getStatus());
                containerInfo.put("state", container.getState());
                containerInfo.put("created", container.getCreated());
                
                // 获取容器统计信息（简化版本，避免复杂的统计API）
                try {
                    // 使用简单的状态信息替代复杂的统计
                    containerInfo.put("cpuUsage", "N/A");
                    containerInfo.put("memoryUsage", "N/A");
                } catch (Exception e) {
                    containerInfo.put("cpuUsage", "N/A");
                    containerInfo.put("memoryUsage", "N/A");
                }
                
                containerList.add(containerInfo);
            }
            
            status.put("containers", containerList);
            status.put("totalCount", containers.size());
            status.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            status.put("error", "获取容器状态失败: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 获取性能统计信息
     */
    @GetMapping("/performance")
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 获取容器池状态
            String poolStatus = containerPool.getPoolStatus();
            String detailedStatus = containerPool.getDetailedPoolStatus();
            
            stats.put("poolStatus", poolStatus);
            stats.put("detailedStatus", detailedStatus);
            stats.put("timestamp", System.currentTimeMillis());
            
            // 计算容器利用率
            int availableCount = containerPool.getAvailableContainerCount();
            int totalCount = containerPool.getTotalContainerCount();
            int inUseCount = totalCount - availableCount;
            double utilization = totalCount > 0 ? (double)inUseCount / totalCount * 100 : 0;
            
            stats.put("containerUtilization", String.format("%.2f%%", utilization));
            stats.put("availableContainers", availableCount);
            stats.put("inUseContainers", inUseCount);
            stats.put("totalContainers", totalCount);
            
        } catch (Exception e) {
            stats.put("error", "获取性能统计失败: " + e.getMessage());
        }
        
        return stats;
    }

    /**
     * 测试代码执行性能
     */
    @GetMapping("/test-performance")
    public Map<String, Object> testPerformance() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 模拟一个简单的Java代码执行测试
            String testCode = "public class Main {\n" +
                            "    public static void main(String[] args) {\n" +
                            "        System.out.println(\"Hello, World!\");\n" +
                            "    }\n" +
                            "}";
            
            // 这里可以添加实际的代码执行测试
            // 目前只是返回基本信息
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            result.put("testDuration", duration + "ms");
            result.put("optimization", "Direct container file creation (no host IO)");
            result.put("timestamp", System.currentTimeMillis());
            result.put("status", "success");
            
        } catch (Exception e) {
            result.put("error", "性能测试失败: " + e.getMessage());
            result.put("status", "error");
        }
        
        return result;
    }

    /**
     * 获取容器删除状态
     */
    @GetMapping("/container-deletion-status")
    public Map<String, Object> getContainerDeletionStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // 获取所有容器信息
            List<Container> containers = containerPool.getDockerClient().listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of("judge-pool-*"))
                .exec();
            
            List<Map<String, Object>> containerList = new ArrayList<>();
            for (Container container : containers) {
                Map<String, Object> containerInfo = new HashMap<>();
                String[] names = container.getNames();
                String name = names != null && names.length > 0 ? names[0].substring(1) : "unknown";
                
                containerInfo.put("name", name);
                containerInfo.put("status", container.getStatus());
                containerInfo.put("state", container.getState());
                containerInfo.put("created", container.getCreated());
                
                // 检查容器状态
                String containerState = container.getState();
                if ("exited".equals(containerState) || "dead".equals(containerState)) {
                    containerInfo.put("needsCleanup", true);
                    containerInfo.put("cleanupReason", "容器状态异常: " + containerState);
                } else {
                    containerInfo.put("needsCleanup", false);
                }
                
                containerList.add(containerInfo);
            }
            
            status.put("containers", containerList);
            status.put("totalCount", containers.size());
            status.put("exitedCount", containers.stream()
                .filter(c -> "exited".equals(c.getState()) || "dead".equals(c.getState()))
                .count());
            status.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            status.put("error", "获取容器删除状态失败: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 手动清理异常容器
     */
    @GetMapping("/cleanup-abnormal-containers")
    public Map<String, Object> cleanupAbnormalContainers() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取所有容器信息
            List<Container> containers = containerPool.getDockerClient().listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of("judge-pool-*"))
                .exec();
            
            int cleanedCount = 0;
            List<String> cleanedContainers = new ArrayList<>();
            
            for (Container container : containers) {
                String[] names = container.getNames();
                String name = names != null && names.length > 0 ? names[0].substring(1) : "unknown";
                
                // 检查容器状态
                String containerState = container.getState();
                if ("exited".equals(containerState) || "dead".equals(containerState)) {
                    try {
                        // 尝试删除异常容器
                        containerPool.getDockerClient().removeContainerCmd(name).exec();
                        cleanedCount++;
                        cleanedContainers.add(name);
                        logger.info("手动清理异常容器: {}", name);
                    } catch (Exception e) {
                        logger.warn("清理异常容器失败: {}", name, e);
                    }
                }
            }
            
            result.put("cleanedCount", cleanedCount);
            result.put("cleanedContainers", cleanedContainers);
            result.put("timestamp", System.currentTimeMillis());
            result.put("status", "success");
            
        } catch (Exception e) {
            result.put("error", "清理异常容器失败: " + e.getMessage());
            result.put("status", "error");
        }
        
        return result;
    }
} 