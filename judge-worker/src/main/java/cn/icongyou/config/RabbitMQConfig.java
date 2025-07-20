package cn.icongyou.config;

import cn.icongyou.Constants;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName RabbitMQConfig
 * @Description RabbitMQ配置文件
 * @Author JiangYang
 * @Date 2025/7/9 19:33
 * @Version 1.0
 **/

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.concurrent-consumers}")
    private int ConcurrentConsumers;

    @Value("${spring.rabbitmq.max-concurrent-consumers}")
    private int MaxConcurrentConsumers;

    @Value("${spring.rabbitmq.prefetch-count}")
    private int PrefetchCount;

    @Bean
    public Queue judgeQueue() {
        return new Queue(Constants.JUDGE_QUEUE, true);
    }

    @Bean
    public Queue resultQueue() {
        return new Queue(Constants.RESULT_QUEUE, true);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        // 设置并发消费者数量，与容器池大小匹配
        factory.setConcurrentConsumers(ConcurrentConsumers);
        factory.setMaxConcurrentConsumers(MaxConcurrentConsumers);
        // 设置预取数量，避免单个消费者获取过多消息
        factory.setPrefetchCount(PrefetchCount);
        // 设置确认模式
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        return factory;
    }
}

