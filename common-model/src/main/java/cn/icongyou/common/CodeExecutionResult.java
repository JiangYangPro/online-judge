package cn.icongyou.common;

import java.io.Serializable;

/**
 * @ClassName CodeExecutionResult
 * @Description 代码执行结果实体
 * @Author JiangYang
 * @Date 2025/7/9 18:42
 * @Version 1.0
 **/
public class CodeExecutionResult implements Serializable {
    private String submissionId;
    private JudgeStatus status;
    private String stdout;
    private String stderr;
    private int exitCode;
    private long executionTimeMs;

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public JudgeStatus getStatus() {
        return status;
    }

    public void setStatus(JudgeStatus status) {
        this.status = status;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
}

