package cn.icongyou.common;

import java.io.Serializable;

/**
 * @ClassName CodeExecutionRequest
 * @Description 代码提交请求实体
 * @Author JiangYang
 * @Date 2025/7/9 18:42
 * @Version 1.0
 **/
public class CodeExecutionRequest implements Serializable {
    private String submissionId;
    private String language;
    private String sourceCode;
    private String stdin;

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getStdin() {
        return stdin;
    }

    public void setStdin(String stdin) {
        this.stdin = stdin;
    }
}

