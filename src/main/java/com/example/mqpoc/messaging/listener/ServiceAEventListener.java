package com.example.mqpoc.messaging.listener;

import com.example.mqpoc.messaging.model.EventEnvelope;
import com.example.mqpoc.messaging.model.payload.OrderCreatedEvent;
import com.example.mqpoc.messaging.model.payload.UserCreatedEvent;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Profile({"service-a", "both", "cross"})
public class ServiceAEventListener {

    public ServiceAEventListener() {
    }

    /**
     * Service A 큐에서 수신된 이벤트를 처리한다. 수동 ACK 모드이며, 예외 발생 시 재큐잉 없이 NACK 처리한다.
     *
     * @param envelope 퍼블리셔가 보낸 {@link com.example.mqpoc.messaging.model.EventEnvelope} (payload와 메타데이터 포함)
     * @param message  원본 AMQP 메시지(헤더/라우팅 키 확인용)
     * @param channel  수동 ACK/NACK 전송을 위한 채널
     * @throws IOException ACK/NACK 전송 실패 시
     */
    @RabbitListener(queues = {"${messaging.services.serviceA.queue}"})
    public void handleOrderCreatedEvent(@Payload EventEnvelope<OrderCreatedEvent> envelope, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            System.out.printf("ServiceA consume: routingKey=%s eventType=%s messageId=%s traceId=%s%n",
                    message.getMessageProperties().getReceivedRoutingKey(),
                    envelope.getEventType(),
                    envelope.getMetadata() != null ? envelope.getMetadata().getMessageId() : "n/a",
                    envelope.getMetadata() != null ? envelope.getMetadata().getTraceId() : "n/a");
            System.out.println(envelope.getPayload().toString());
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            System.err.printf("ServiceA handler error: %s%n", ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @Profile("cross")
    @RabbitListener(queues = {"${messaging.services.serviceBToA.queue}"})
    public void handleUserCreatedEvent(@Payload EventEnvelope<UserCreatedEvent> envelope, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            System.out.printf("ServiceA consume: routingKey=%s eventType=%s messageId=%s traceId=%s%n",
                    message.getMessageProperties().getReceivedRoutingKey(),
                    envelope.getEventType(),
                    envelope.getMetadata() != null ? envelope.getMetadata().getMessageId() : "n/a",
                    envelope.getMetadata() != null ? envelope.getMetadata().getTraceId() : "n/a");
            System.out.println(envelope.getPayload().toString());
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            System.err.printf("ServiceA handler error: %s%n", ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
