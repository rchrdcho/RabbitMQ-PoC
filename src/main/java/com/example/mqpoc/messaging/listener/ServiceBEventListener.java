package com.example.mqpoc.messaging.listener;

import com.example.mqpoc.messaging.model.EventEnvelope;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Profile({"service-b", "both", "cross"})
public class ServiceBEventListener {

    public ServiceBEventListener() {
    }

    @RabbitListener(queues = "${messaging.services.serviceB.queue}")
    public void onMessage(@Payload EventEnvelope<?> envelope, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            System.out.printf("ServiceB consume: routingKey=%s eventType=%s messageId=%s traceId=%s%n",
                    message.getMessageProperties().getReceivedRoutingKey(),
                    envelope.getEventType(),
                    envelope.getMetadata() != null ? envelope.getMetadata().getMessageId() : "n/a",
                    envelope.getMetadata() != null ? envelope.getMetadata().getTraceId() : "n/a");
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            System.err.printf("ServiceB handler error: %s%n", ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
