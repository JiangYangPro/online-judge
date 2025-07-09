package cn.icongyou.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName RabbitMQConfig
 * @Description Redis配置文件
 * @Author JiangYang
 * @Date 2025/7/9 19:17
 * @Version 1.0
 **/

@Configuration
public class RabbitMQConfig {

    public static final String JUDGE_QUEUE = "judge.queue";

    @Bean
    public Queue judgeQueue() {
        return new Queue(JUDGE_QUEUE, true); // durable queue
    }
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

