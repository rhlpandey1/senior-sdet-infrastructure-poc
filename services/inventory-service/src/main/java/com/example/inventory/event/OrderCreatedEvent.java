package com.example.inventory.event;

public record OrderCreatedEvent(
        String eventType,
        String orderId,
        String product,
        Integer quantity
) {
}
