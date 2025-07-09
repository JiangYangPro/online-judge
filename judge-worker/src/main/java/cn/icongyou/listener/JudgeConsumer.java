package cn.icongyou.listener;

import cn.icongyou.common.CodeExecutionRequest;
import cn.icongyou.common.CodeExecutionResult;
import cn.icongyou.executor.JavaCodeExecutor;
import cn.icongyou.messaging.JudgeResultProducer;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @ClassName JudgeConsumer
 * @Description TODO
 * @Author JiangYang
 * @Date 2025/7/9 19:31
 * @Version 1.0
 **/

@Component
public class JudgeConsumer {

    private final JudgeResultProducer producer;
    private final JavaCodeExecutor executor = new JavaCodeExecutor();

    public JudgeConsumer(JudgeResultProducer producer) {
        this.producer = producer;
    }

    @RabbitListener(queues = "judge.queue")
    public void onMessage(CodeExecutionRequest request) {
        CodeExecutionResult result = executor.execute(request);
        producer.sendResult(result);
    }
}

