package cn.icongyou.messaging;

import cn.icongyou.common.CodeExecutionResult;
import cn.icongyou.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * @ClassName JudgeResultProducer
 * @Description TODO
 * @Author JiangYang
 * @Date 2025/7/9 19:33
 * @Version 1.0
 **/

@Component
public class JudgeResultProducer {

    private final RabbitTemplate rabbitTemplate;

    public JudgeResultProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendResult(CodeExecutionResult result) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.RESULT_QUEUE, result);
    }
}

