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

import com.example.mqpoc.messaging.model.payload.UserCreatedEvent;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-b/events")
@Profile({"service-b", "both", "cross"})
public class ServiceBPublishController {

    private static final String DEFAULT_EVENT = "user.created";

    private final EventPublisher publisher;

    public ServiceBPublishController(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> publish(@Valid @RequestBody PublishRequest request) {
        publisher.publish("serviceB", request.getEventType(), request.getPayload(), request.getTraceId());
        return ResponseEntity.accepted().body(Map.of(
                "service", "serviceB",
                "eventType", request.getEventType()
        ));
    }

    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> publishSample() {
        UserCreatedEvent samplePayload = new UserCreatedEvent(
                "USER-" + UUID.randomUUID(),
                "user+" + UUID.randomUUID().toString().substring(0, 6) + "@example.com",
                "Sample User",
                "ACTIVE"
        );

        publisher.publish("serviceB", DEFAULT_EVENT, samplePayload, null);
        return ResponseEntity.ok().build();
    }
}
