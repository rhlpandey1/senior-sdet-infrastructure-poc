package com.example.order.event;

public record OrderCreatedEvent(
        String eventType,
        String orderId,
        String product,
        Integer quantity
) {
}
