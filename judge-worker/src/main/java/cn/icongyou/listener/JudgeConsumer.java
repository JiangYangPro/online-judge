package cn.icongyou.listener;

import cn.icongyou.common.CodeExecutionRequest;
import cn.icongyou.common.CodeExecutionResult;
import cn.icongyou.common.JudgeStatus;
import cn.icongyou.executor.JavaCodeExecutor;
import cn.icongyou.messaging.JudgeResultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName JudgeConsumer
 * @Description 判题消费者
 * @Author JiangYang
 * @Date 2025/7/9 19:31
 * @Version 2.0
 **/

@Component
public class JudgeConsumer {
    private static final Logger logger = LoggerFactory.getLogger(JudgeConsumer.class);

    private final JudgeResultProducer producer;
    
    @Autowired
    private JavaCodeExecutor executor;

    public JudgeConsumer(JudgeResultProducer producer) {
        this.producer = producer;
    }

    @RabbitListener(queues = "judge.queue")
    public void onMessage(CodeExecutionRequest request) {
        try {
            logger.info("开始处理提交ID: {}", request.getSubmissionId());
            
            // 异步执行代码
            Future<CodeExecutionResult> future = executor.execute(request);
            
            // 等待执行结果，设置30秒超时
            CodeExecutionResult result = future.get(30, TimeUnit.SECONDS);
            
            // 发送结果
            producer.sendResult(result);
            
            logger.info("提交ID: {} 处理完成，状态: {}", request.getSubmissionId(), result.getStatus());
            
        } catch (Exception e) {
            logger.error("提交ID: {} 处理失败", request.getSubmissionId(), e);
            
            // 创建错误结果
            CodeExecutionResult errorResult = new CodeExecutionResult();
            errorResult.setSubmissionId(request.getSubmissionId());
            errorResult.setStatus(JudgeStatus.INTERNAL_ERROR);
            errorResult.setStderr("处理失败: " + e.getMessage());
            
            // 发送错误结果
            producer.sendResult(errorResult);
        }
    }
}

