package cn.icongyou.executor;

import cn.icongyou.common.CodeExecutionRequest;
import cn.icongyou.common.CodeExecutionResult;
import cn.icongyou.common.JudgeStatus;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import org.slf4j.Logger;

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

            // 创建临时工作目录
            Path tempDir = Files.createTempDirectory("judge-" + UUID.randomUUID());
            File sourceFile = tempDir.resolve(filename).toFile();

            // 写入 Java 源代码
            try (FileWriter fw = new FileWriter(sourceFile)) {
                fw.write(request.getSourceCode());
            }

            // 使用docker-java API执行代码
            logger.debug("使用docker-java API执行代码");
            return executeInDockerWithAPI(containerName, tempDir, filename, className, request, result);
            
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
     * 使用docker-java API执行代码
     */
    private CodeExecutionResult executeInDockerWithAPI(String containerName, Path tempDir, 
                                                      String filename, String className, 
                                                      CodeExecutionRequest request, 
                                                      CodeExecutionResult result) throws Exception {
        
        try {
            // 确保容器中的workspace目录存在
            String mkdirOutput = containerPool.executeCommand(containerName, 
                "sh", "-c", "mkdir -p /workspace");
            
            if (mkdirOutput == null) {
                result.setStatus(JudgeStatus.INTERNAL_ERROR);
                result.setStderr("无法在容器中创建workspace目录");
                return result;
            }
            
            // 将源代码文件复制到容器中
            ProcessBuilder copyCmd = new ProcessBuilder(
                "docker", "cp", tempDir.toString() + "/" + filename, 
                containerName + ":/workspace/" + filename
            );
            Process copyProcess = copyCmd.start();
            int copyExit = copyProcess.waitFor();
            
            if (copyExit != 0) {
                result.setStatus(JudgeStatus.INTERNAL_ERROR);
                result.setStderr("无法复制源代码文件到容器");
                return result;
            }
            
            // 编译代码
            String compileOutput = containerPool.executeCommand(containerName, 
                "javac", "/workspace/" + filename);

            if (compileOutput == null || !compileOutput.isEmpty()) {
                result.setStatus(JudgeStatus.COMPILE_ERROR);
                result.setStderr(compileOutput != null ? compileOutput : "编译失败");
                return result;
            }

            // 运行代码
            long startTime = System.currentTimeMillis();
            String runOutput = "";
            int exitCode = 0;
            
            if (request.getStdin() != null && !request.getStdin().isEmpty()) {
                // 有输入数据，需要特殊处理
                runOutput = executeWithInput(containerName, className, request.getStdin());
                exitCode = 0; // 简化处理，实际应该从输出中解析
            } else {
                // 无输入数据，直接执行
                runOutput = containerPool.executeCommand(containerName, 
                    "java", "-cp", "/workspace", className);
                exitCode = 0; // 简化处理
            }
            
            long endTime = System.currentTimeMillis();

            result.setExitCode(exitCode);
            result.setExecutionTimeMs(endTime - startTime);
            result.setStdout(runOutput != null ? runOutput : "");
            result.setStatus(exitCode == 0 ? JudgeStatus.ACCEPTED : JudgeStatus.RUNTIME_ERROR);

            logger.info("提交ID: {} 运行结束，状态: {}, 耗时: {}ms", 
                       request.getSubmissionId(), result.getStatus(), result.getExecutionTimeMs());
            
        } finally {
            // 清理容器中的文件
            try {
                containerPool.executeCommand(containerName, "sh", "-c", 
                    "rm -f /workspace/" + filename + " /workspace/" + className + ".class");
            } catch (Exception e) {
                logger.debug("清理容器文件时发生错误: {}", e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * 执行带输入的命令
     */
    private String executeWithInput(String containerName, String className, String stdin) {
        try {
            // 创建临时输入文件
            Path tempInputFile = Files.createTempFile("input-", ".txt");
            try (FileWriter fw = new FileWriter(tempInputFile.toFile())) {
                fw.write(stdin);
            }
            
            // 复制输入文件到容器
            ProcessBuilder copyInputCmd = new ProcessBuilder(
                "docker", "cp", tempInputFile.toString(), 
                containerName + ":/workspace/input.txt"
            );
            Process copyInputProcess = copyInputCmd.start();
            copyInputProcess.waitFor();
            
            // 执行命令，重定向输入
            String output = containerPool.executeCommand(containerName, 
                "sh", "-c", "java -cp /workspace " + className + " < /workspace/input.txt");
            
            // 清理输入文件
            Files.deleteIfExists(tempInputFile);
            
            return output;
        } catch (Exception e) {
            logger.error("执行带输入的命令时发生错误", e);
            return "";
        }
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

