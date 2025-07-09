package cn.icongyou.executor;

import cn.icongyou.common.CodeExecutionRequest;
import cn.icongyou.common.CodeExecutionResult;
import cn.icongyou.common.JudgeStatus;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @ClassName JavaCodeExecutor
 * @Description TODO
 * @Author JiangYang
 * @Date 2025/7/9 19:32
 * @Version 1.0
 **/

public class JavaCodeExecutor {
    private static final Logger logger = LoggerFactory.getLogger(JavaCodeExecutor.class);
    public CodeExecutionResult execute(CodeExecutionRequest request) {
        logger.info("开始处理提交ID: {}", request.getSubmissionId());
        CodeExecutionResult result = new CodeExecutionResult();
        result.setSubmissionId(request.getSubmissionId());
        String className = "Main";
        String filename = className + ".java";

        try {
            // 创建临时工作目录
            Path tempDir = Files.createTempDirectory("judge-" + UUID.randomUUID());
            File sourceFile = tempDir.resolve(filename).toFile();

            // 写入 Java 源代码
            try (FileWriter fw = new FileWriter(sourceFile)) {
                fw.write(request.getSourceCode());
            }

            // 编译
            Process compile = new ProcessBuilder("javac", filename)
                    .directory(tempDir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String compileOutput = new String(compile.getInputStream().readAllBytes());
            int compileExit = compile.waitFor();

            if (compileExit != 0) {
                result.setStatus(JudgeStatus.COMPILE_ERROR);
                result.setStderr(compileOutput);
                return result;
            }

            // 运行
            Process run = new ProcessBuilder("java", className)
                    .directory(tempDir.toFile())
                    .redirectErrorStream(true)
                    .start();

            // 提供输入
            if (request.getStdin() != null) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(run.getOutputStream()))) {
                    writer.write(request.getStdin());
                    writer.flush();
                }
            }

            long startTime = System.currentTimeMillis();
            String output = new String(run.getInputStream().readAllBytes());
            int exitCode = run.waitFor();
            long endTime = System.currentTimeMillis();

            result.setExitCode(exitCode);
            result.setExecutionTimeMs(endTime - startTime);
            result.setStdout(output);
            result.setStatus(exitCode == 0 ? JudgeStatus.ACCEPTED : JudgeStatus.RUNTIME_ERROR);

            logger.info("提交ID: {} 运行结束，状态: {}, 耗时: {}ms", request.getSubmissionId(), result.getStatus(), result.getExecutionTimeMs());
        } catch (Exception e) {
            logger.error("提交ID: {} 执行时发生异常", request.getSubmissionId(), e);
            result.setStatus(JudgeStatus.INTERNAL_ERROR);
            result.setStderr(e.getMessage());
        }

        return result;
    }
}

