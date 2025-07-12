package cn.icongyou.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName RabbitMQConfig
 * @Description RabbitMQ配置文件
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

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        // 启用发布确认
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // 处理发送失败的情况
                System.err.println("消息发送失败: " + cause);
            }
        });
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        // 设置并发消费者数量
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(10);
        // 设置预取数量
        factory.setPrefetchCount(10);
        return factory;
    }
}

