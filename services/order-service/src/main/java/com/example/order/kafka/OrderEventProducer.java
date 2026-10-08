package com.example.order.kafka;

import com.example.order.event.OrderCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventProducer {

    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(
                    TOPIC,
                    event.orderId(),
                    json
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize ORDER_CREATED event", e);
        }
    }
}
