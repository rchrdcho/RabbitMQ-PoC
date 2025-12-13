package com.example.mqpoc.messaging.model.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record 형태의 이벤트 샘플.
 * <ul>
 *     <li>모든 필드는 생성자 파라미터로 고정되며, JSON에 누락되면 역직렬화 오류가 발생한다. (레코드는 불변이고
 *     디폴트 생성자가 없으므로, 직렬화 시점에 모든 생성자 파라미터가 채워져야 한다.)</li>
 *     <li>필드명이 JSON 키와 다르면 {@link JsonProperty}를 붙여야 한다.</li>
 *     <li>기본값/세터가 없으므로 필수 필드만 정의하거나, 선택 필드는 nullable/옵션 처리해야 한다. (선택 필드를
 *     누락하면 역직렬화 예외가 발생할 수 있으니, Optional/nullable 타입으로 모델링하거나 별도 DTO로 분리)</li>
 * </ul>
 */
public record UserCreatedEvent(
        String userId,
        String email,
        String name,
        String status
) {
}
