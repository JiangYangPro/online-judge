package cn.icongyou.messaging;

import cn.icongyou.common.CodeExecutionRequest;
import cn.icongyou.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * @ClassName JudgeProducer
 * @Description TODO
 * @Author JiangYang
 * @Date 2025/7/9 19:17
 * @Version 1.0
 **/

@Component
public class JudgeProducer {

    private final RabbitTemplate rabbitTemplate;

    public JudgeProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(CodeExecutionRequest request) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.JUDGE_QUEUE, request);
    }
}

