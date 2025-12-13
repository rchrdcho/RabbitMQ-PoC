package com.example.mqpoc.messaging.publisher;

import com.example.mqpoc.config.MessagingProperties;
import com.example.mqpoc.messaging.model.EventEnvelope;
import com.example.mqpoc.messaging.model.EventMetadata;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    public EventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    /**
     * 서비스별 exchange/prefix 규칙에 맞춰 이벤트를 발행한다.
     * <ul>
     *     <li>라우팅 키: {@code <prefix>.<eventName>} (예: {@code serviceA.order.created})</li>
     *     <li>헤더: {@link #buildHeaders(String, String, String, MessagingProperties.Service, Instant)}에서 추적/라우팅용 헤더를 설정</li>
     *     <li>바디: {@link #buildEnvelope(String, Object, MessagingProperties.Service, String, String, Instant)}에서 payload를 감싸고 메타데이터 생성</li>
     *     <li>Confirm/Return: CorrelationData에 messageId를 넣어 confirm 로그에서 추적 가능</li>
     * </ul>
     * 사용 예:
     * <pre>{@code
     * // traceId 미지정 시 자동 생성
     * publisher.publish("serviceA", "order.created", payloadMap, null);
     *
     * publisher.publish("serviceB", "user.created", payloadMap, "existing-trace-id");
     * }</pre>
     *
     * @param serviceKey messaging.services.* 설정 키 (예: serviceA, serviceB)
     * @param eventName  라우팅 키에 붙을 이벤트 이름 (prefix 제외)
     * @param payload    전송할 페이로드 객체/맵
     * @param traceId    null이면 신규 생성, 값이 있으면 correlationId/헤더/metadata에 반영
     * @param <T>        페이로드 타입
     */
    public <T> void publish(String serviceKey, String eventName, T payload, String traceId) {
        MessagingProperties.Service service = properties.services().get(serviceKey);
        Assert.notNull(service, "Unknown service key: " + serviceKey);
        Assert.hasText(eventName, "eventName must not be blank");
        String routingKey = service.prefix() + "." + eventName;

        String messageId = UUID.randomUUID().toString();
        String resolvedTraceId = StringUtils.hasText(traceId) ? traceId : UUID.randomUUID().toString();
        Instant now = Instant.now();

        MessagePostProcessor headers = buildHeaders(messageId, resolvedTraceId, eventName, service, now);
        EventEnvelope<T> envelope = buildEnvelope(eventName, payload, service, messageId, resolvedTraceId, now);

        System.out.printf("Publish: service=%s exchange=%s routingKey=%s eventType=%s messageId=%s traceId=%s%n",
                serviceKey, service.exchange(), routingKey, eventName, messageId, resolvedTraceId);

        rabbitTemplate.convertAndSend(service.exchange(), routingKey, envelope, headers, new CorrelationData(messageId));
    }

    /**
     * AMQP 프로퍼티/헤더를 설정하는 {@link MessagePostProcessor}를 생성한다.
     * <ul>
     *     <li>{@code messageId}, {@code correlationId(traceId)}, {@code type(eventName)}, {@code appId(sourceService)}</li>
     *     <li>{@code timestamp}, {@code content-type=json}, {@code content-encoding=utf-8}, {@code deliveryMode=PERSISTENT}</li>
     *     <li>커스텀 헤더: {@code traceId}, {@code schema-version}</li>
     * </ul>
     *
     * @param messageId 메시지 식별자
     * @param traceId   추적/상관 ID
     * @param eventName 이벤트명(헤더 type)
     * @param service   서비스 설정(sourceService, schemaVersion 등)
     * @param timestamp 헤더 timestamp 설정에 사용될 시각
     * @return 헤더를 설정하는 MessagePostProcessor
     */
    private MessagePostProcessor buildHeaders(
            String messageId,
            String traceId,
            String eventName,
            MessagingProperties.Service service,
            Instant timestamp
    ) {
        return message -> {
            MessageProperties mp = message.getMessageProperties();
            mp.setMessageId(messageId);
            mp.setCorrelationId(traceId);
            mp.setType(eventName);
            mp.setAppId(service.sourceService());
            mp.setTimestamp(Date.from(timestamp));
            mp.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            mp.setContentEncoding("utf-8");
            mp.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            mp.setHeader("traceId", traceId);
            mp.setHeader("schema-version", service.schemaVersion());
            return message;
        };
    }

    /**
     * 이벤트 바디와 메타데이터를 조립해 {@link EventEnvelope}를 생성한다.
     * <ul>
     *     <li>{@code eventType} = eventName</li>
     *     <li>{@code occurredAt}/{@code publishedAt} = timestamp</li>
     *     <li>{@code metadata}: messageId/traceId/sourceService/schemaVersion, priority=normal, sensitivity=internal</li>
     * </ul>
     *
     * @param eventName 라우팅 키에 붙는 이벤트명 (prefix 제외)
     * @param payload   전송할 도메인 객체
     * @param service   서비스별 설정 (prefix/sourceService/schemaVersion 등)
     * @param messageId 메시지 식별자(멱등/추적용)
     * @param traceId   추적용 ID (correlationId와 동일하게 사용)
     * @param timestamp 발생/발행 시각
     * @param <T>       페이로드 타입
     * @return 조립된 이벤트 래퍼
     */
    private <T> EventEnvelope<T> buildEnvelope(
            String eventName,
            T payload,
            MessagingProperties.Service service,
            String messageId,
            String traceId,
            Instant timestamp
    ) {
        EventEnvelope<T> envelope = new EventEnvelope<>();
        envelope.setEventType(eventName);
        envelope.setOccurredAt(timestamp);
        envelope.setPublishedAt(timestamp);
        envelope.setPayload(payload);

        EventMetadata metadata = new EventMetadata();
        metadata.setMessageId(messageId);
        metadata.setTraceId(traceId);
        metadata.setSourceService(service.sourceService());
        metadata.setSchemaVersion(service.schemaVersion());
        metadata.setPriority("normal");
        metadata.setSensitivity("internal");
        envelope.setMetadata(metadata);
        return envelope;
    }
}
