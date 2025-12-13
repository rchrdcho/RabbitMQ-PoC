package com.example.mqpoc.messaging.model.payload;

import java.util.ArrayList;
import java.util.List;

/**
 * 클래스 기반 이벤트 샘플 (불변 요구가 없고, 선택 필드나 디폴트 값이 필요할 때 사용).
 * <ul>
 *     <li>세터를 통해 선택 필드를 유연하게 채울 수 있다.</li>
 *     <li>JSON 역직렬화 시 필드 누락이 있어도 객체 생성이 가능하며, 필요 시 기본값/nullable 처리로 대응한다.</li>
 *     <li>필드명이 JSON 키와 다르면 {@code @JsonProperty}로 매핑할 수 있다.</li>
 * </ul>
 */
public class OrderCreatedEvent {
    private String orderId;
    private String customerId;
    private int totalAmount;
    private String currency;
    private List<OrderItem> items = new ArrayList<>();

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(int totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
