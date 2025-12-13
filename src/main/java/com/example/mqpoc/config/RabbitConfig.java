package com.example.mqpoc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
@EnableRabbit
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitConfig {

    @Value("${spring.application.name:mq-poc}")
    private String appName;

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback(this::logConfirm);
        rabbitTemplate.setReturnsCallback(returned -> {
            // Keep logging lightweight; PoC focus is visibility.
            System.out.printf("Return callback: exchange=%s routingKey=%s replyCode=%s replyText=%s messageId=%s%n",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText(),
                    returned.getMessage().getMessageProperties().getMessageId());
        });
        return rabbitTemplate;
    }

    private void logConfirm(CorrelationData correlationData, boolean ack, String cause) {
        String messageId = correlationData != null ? correlationData.getId() : "n/a";
        System.out.printf("Confirm callback: ack=%s messageId=%s cause=%s%n", ack, messageId, cause);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            CachingConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(1);
        return factory;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(CachingConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    @Primary
    public Declarables topology(MessagingProperties properties) {
        List<Declarable> declarables = new ArrayList<>();
        // Shared DLX/DLQ
        TopicExchange dlx = ExchangeBuilder.topicExchange(properties.shared().dlx()).durable(true).build();
        Queue dlq = QueueBuilder.durable(properties.shared().dlq()).build();
        declarables.add(dlx);
        declarables.add(dlq);
        declarables.add(BindingBuilder.bind(dlq).to(dlx).with("#"));

        // Service-specific exchanges/queues/bindings
        Function<String, String> deadKey = prefix -> prefix + ".dead";

        for (MessagingProperties.Service service : properties.services().values()) {
            TopicExchange exchange = ExchangeBuilder.topicExchange(service.exchange()).durable(true).build();
            Map<String, Object> args = new HashMap<>();
            args.put("x-dead-letter-exchange", properties.shared().dlx());
            args.put("x-dead-letter-routing-key", deadKey.apply(service.prefix()));
            Queue queue = QueueBuilder.durable(service.queue()).withArguments(args).build();
            Binding binding = BindingBuilder.bind(queue).to(exchange).with(service.prefix() + ".#");
            Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(deadKey.apply(service.prefix()));
            declarables.add(exchange);
            declarables.add(queue);
            declarables.add(binding);
            declarables.add(dlqBinding);
        }
        return new Declarables(declarables);
    }
}
