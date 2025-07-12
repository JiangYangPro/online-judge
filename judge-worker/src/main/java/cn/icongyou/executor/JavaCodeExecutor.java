package cn.icongyou.executor;

import cn.icongyou.common.CodeExecutionRequest;
import cn.icongyou.common.CodeExecutionResult;
import cn.icongyou.common.JudgeStatus;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import org.slf4j.Logger;
import java.util.concurrent.CompletableFuture;

/**
 * @ClassName JavaCodeExecutor
 * @Description 优化的Java代码执行器，使用docker-java API
 * @Author JiangYang
 * @Date 2025/7/9 19:32
 * @Version 2.0
 **/

@Component
public class JavaCodeExecutor {
    private static final Logger logger = LoggerFactory.getLogger(JavaCodeExecutor.class);
    
    @Autowired
    private DockerContainerPool containerPool;
    
    // 线程池配置
    private static final ExecutorService executorService = new ThreadPoolExecutor(
        10, // 核心线程数
        20, // 最大线程数
        60L, // 空闲线程存活时间
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100), // 工作队列
        new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
    );
    
    /**
     * 异步执行代码
     */
    public Future<CodeExecutionResult> execute(CodeExecutionRequest request) {
        return executorService.submit(() -> {
            try {
                return executeCode(request);
            } catch (Exception e) {
                logger.error("提交ID: {} 执行失败", request.getSubmissionId(), e);
                CodeExecutionResult result = new CodeExecutionResult();
                result.setSubmissionId(request.getSubmissionId());
                result.setStatus(JudgeStatus.INTERNAL_ERROR);
                result.setStderr(e.getMessage());
                return result;
            }
        });
    }
    
    private CodeExecutionResult executeCode(CodeExecutionRequest request) throws Exception {
        CodeExecutionResult result = new CodeExecutionResult();
        result.setSubmissionId(request.getSubmissionId());
        
        String className = "Main";
        String filename = className + ".java";
        String containerName = null;

        try {
            // 从容器池获取容器
            containerName = containerPool.acquireContainer();
            if (containerName == null) {
                result.setStatus(JudgeStatus.INTERNAL_ERROR);
                result.setStderr("无法获取可用的执行容器");
                return result;
            }

            // 直接在容器内创建和执行代码，避免主机IO操作
            logger.debug("直接在容器内创建和执行代码");
            return executeInDockerDirectly(containerName, filename, className, request, result);
            
        } catch (Exception e) {
            logger.error("提交ID: {} 执行时发生异常", request.getSubmissionId(), e);
            result.setStatus(JudgeStatus.INTERNAL_ERROR);
            result.setStderr(e.getMessage());
            return result;
        } finally {
            // 释放容器回池中
            if (containerName != null) {
                containerPool.releaseContainer(containerName);
            }
        }
    }
    
    /**
     * 直接在容器内创建和执行代码（优化版本）
     */
    private CodeExecutionResult executeInDockerDirectly(String containerName, String filename, 
                                                       String className, CodeExecutionRequest request, 
                                                       CodeExecutionResult result) throws Exception {
        
        try {
            // 异步创建workspace目录
            CompletableFuture<String> mkdirFuture = containerPool.executeCommandAsync(containerName, 
                "sh", "-c", "mkdir -p /workspace");
            
            // 等待目录创建完成
            String mkdirOutput = mkdirFuture.get(10, TimeUnit.SECONDS);
            
            if (mkdirOutput == null) {
                result.setStatus(JudgeStatus.INTERNAL_ERROR);
                result.setStderr("无法在容器中创建workspace目录");
                return result;
            }
            
            // 直接在容器内创建Java源文件
            // 使用base64编码避免shell注入和特殊字符问题
            String base64Code = java.util.Base64.getEncoder().encodeToString(
                request.getSourceCode().getBytes("UTF-8"));
            
            CompletableFuture<String> createFileFuture = containerPool.executeCommandAsync(containerName, 
                "sh", "-c", "echo '" + base64Code + "' | base64 -d > /workspace/" + filename);
            
            // 等待文件创建完成
            String createFileOutput = createFileFuture.get(10, TimeUnit.SECONDS);
            
            if (createFileOutput == null) {
                result.setStatus(JudgeStatus.INTERNAL_ERROR);
                result.setStderr("无法在容器中创建源文件");
                return result;
            }
            
            // 异步编译代码
            CompletableFuture<String> compileFuture = containerPool.executeCommandAsync(containerName, 
                "javac", "/workspace/" + filename);
            
            // 等待编译完成
            String compileOutput = compileFuture.get(30, TimeUnit.SECONDS);

            if (compileOutput == null || !compileOutput.isEmpty()) {
                result.setStatus(JudgeStatus.COMPILE_ERROR);
                result.setStderr(compileOutput != null ? compileOutput : "编译失败");
                return result;
            }

            // 异步运行代码
            long startTime = System.currentTimeMillis();
            CompletableFuture<String> runFuture;
            
            if (request.getStdin() != null && !request.getStdin().isEmpty()) {
                // 有输入数据，直接在容器内处理
                runFuture = executeWithInputDirectly(containerName, className, request.getStdin());
            } else {
                // 无输入数据，直接执行
                runFuture = containerPool.executeCommandAsync(containerName, 
                    "java", "-cp", "/workspace", className);
            }
            
            // 等待运行完成
            String runOutput = runFuture.get(30, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            result.setExitCode(0); // 简化处理
            result.setExecutionTimeMs(endTime - startTime);
            result.setStdout(runOutput != null ? runOutput : "");
            result.setStatus(JudgeStatus.ACCEPTED);

            logger.info("提交ID: {} 运行结束，状态: {}, 耗时: {}ms", 
                       request.getSubmissionId(), result.getStatus(), result.getExecutionTimeMs());
            
        } finally {
            // 异步清理容器中的文件
            CompletableFuture.runAsync(() -> {
                try {
                    containerPool.executeCommandAsync(containerName, "sh", "-c", 
                        "rm -f /workspace/" + filename + " /workspace/" + className + ".class");
                } catch (Exception e) {
                    logger.debug("清理容器文件时发生错误: {}", e.getMessage());
                }
            });
        }
        
        return result;
    }
    
    /**
     * 直接在容器内处理带输入的执行（优化版本）
     */
    private CompletableFuture<String> executeWithInputDirectly(String containerName, String className, String stdin) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 直接在容器内创建输入文件
                // 使用base64编码避免shell注入和特殊字符问题
                String base64Stdin = java.util.Base64.getEncoder().encodeToString(
                    stdin.getBytes("UTF-8"));
                
                CompletableFuture<String> createInputFuture = containerPool.executeCommandAsync(containerName, 
                    "sh", "-c", "echo '" + base64Stdin + "' | base64 -d > /workspace/input.txt");
                
                // 等待输入文件创建完成
                String createInputOutput = createInputFuture.get(10, TimeUnit.SECONDS);
                
                if (createInputOutput == null) {
                    logger.error("无法在容器中创建输入文件");
                    return "";
                }
                
                // 异步执行命令，重定向输入
                CompletableFuture<String> outputFuture = containerPool.executeCommandAsync(containerName, 
                    "sh", "-c", "java -cp /workspace " + className + " < /workspace/input.txt");
                
                String output = outputFuture.get(30, TimeUnit.SECONDS);
                
                // 异步清理输入文件
                CompletableFuture.runAsync(() -> {
                    try {
                        containerPool.executeCommandAsync(containerName, "sh", "-c", "rm -f /workspace/input.txt");
                    } catch (Exception e) {
                        logger.debug("清理输入文件时发生错误: {}", e.getMessage());
                    }
                });
                
                return output;
            } catch (Exception e) {
                logger.error("直接在容器内执行带输入的命令时发生错误", e);
                return "";
            }
        });
    }
    
    // 关闭线程池
    public static void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}

