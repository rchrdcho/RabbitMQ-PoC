package com.example.mqpoc.messaging.model;

/**
 * 이벤트 추적/스키마/민감도 정보를 담는 메타데이터.
 * <ul>
 *     <li>{@code sourceService}: 발신 서비스 식별자.</li>
 *     <li>{@code traceId}: 분산 추적용 ID.</li>
 *     <li>{@code messageId}: 멱등/추적용 메시지 ID.</li>
 *     <li>{@code schemaVersion}: 메시지 스키마 버전.</li>
 *     <li>{@code priority}: 우선순위 힌트.</li>
 *     <li>{@code sensitivity}: 민감도 레이블(예: internal).</li>
 * </ul>
 * <p>AMQP 표준 프로퍼티(예: content-type, deliveryMode, expiration/ttl, priority, headers 등)는
 * {@link org.springframework.amqp.core.MessageProperties}에 설정하며, 이 클래스는 애플리케이션
 * 레벨에서 추가로 들고 가는 정보만 담는다.</p>
 */
public class EventMetadata {

    private String sourceService;
    private String traceId;
    private String messageId;
    private String schemaVersion;
    private String priority;
    private String sensitivity;

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(String sensitivity) {
        this.sensitivity = sensitivity;
    }
}
