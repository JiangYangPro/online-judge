package cn.icongyou.controller;

import cn.icongyou.common.CodeExecutionRequest;
import cn.icongyou.common.CodeExecutionResult;
import cn.icongyou.messaging.JudgeProducer;
import cn.icongyou.service.ResultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * @ClassName JudgeController
 * @Description 创建提交接口
 * @Author JiangYang
 * @Date 2025/7/9 19:18
 * @Version 1.0
 **/

@RestController
@RequestMapping("/judge")
public class JudgeController {
    @Autowired
    private ResultService resultService;
    private final JudgeProducer producer;

    public JudgeController(JudgeProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/submit")
    public ResponseEntity<String> submit(@RequestBody CodeExecutionRequest request) {
        // 生成 submissionId
        // TODO 应用中需要系统提供该 SubmissionId
        request.setSubmissionId(UUID.randomUUID().toString());
        producer.send(request);
        return ResponseEntity.ok("Submission accepted: " + request.getSubmissionId());
    }

    @GetMapping("/result/{submissionId}")
    public ResponseEntity<CodeExecutionResult> getResult(@PathVariable("submissionId") String submissionId) {
        CodeExecutionResult result = resultService.getResult(submissionId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}

