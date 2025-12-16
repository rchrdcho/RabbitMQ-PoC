package com.example.mqpoc.web;

import com.example.mqpoc.messaging.publisher.EventPublisher;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;

import com.example.mqpoc.messaging.model.payload.OrderCreatedEvent;
import com.example.mqpoc.messaging.model.payload.OrderItem;

import java.util.Map;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/service-a/events")
@Profile({"service-a", "both", "cross"})
public class ServiceAPublishController {

    private static final String DEFAULT_EVENT = "order.created";

    private final EventPublisher publisher;

    public ServiceAPublishController(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> publish(@Valid @RequestBody PublishRequest request) {
        publisher.publish("serviceA", request.getEventType(), request.getPayload(), request.getTraceId());
        return ResponseEntity.accepted().body(Map.of(
                "service", "serviceA",
                "eventType", request.getEventType()
        ));
    }

    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> publishSample() {
        OrderCreatedEvent samplePayload = new OrderCreatedEvent();
        samplePayload.setOrderId("ORD-" + UUID.randomUUID());
        samplePayload.setCustomerId("C-" + UUID.randomUUID().toString().substring(0, 8));
        samplePayload.setTotalAmount(12000);
        samplePayload.setCurrency("KRW");

        OrderItem item = new OrderItem();
        item.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 4));
        item.setQty(1);
        item.setPrice(12000);
        samplePayload.setItems(List.of(item));

        publisher.publish("serviceA", DEFAULT_EVENT, samplePayload, null);
        return ResponseEntity.ok().build();
    }
}
