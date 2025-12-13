package com.example.mqpoc.messaging.model;

import java.time.Instant;

/**
 * 퍼블리셔가 payload를 감싸서 전송하는 공통 이벤트 래퍼.
 * <ul>
 *     <li>{@code eventType}: 라우팅 키의 이벤트명과 일치 (예: order.created).</li>
 *     <li>{@code occurredAt}/{@code publishedAt}: 발생/발행 시각.</li>
 *     <li>{@code payload}: 실제 도메인 이벤트 객체.</li>
 *     <li>{@code metadata}: 출처/추적/스키마/우선순위 등 메타데이터.</li>
 * </ul>
 *
 * @param <T> 도메인 페이로드 타입
 */
public class EventEnvelope<T> {

    private String eventType;
    private Instant occurredAt;
    private Instant publishedAt;
    private T payload;
    private EventMetadata metadata;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    public EventMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(EventMetadata metadata) {
        this.metadata = metadata;
    }
}
