package com.example.order.dto;

public record OrderResponse(
        String id,
        String customerName,
        String product,
        Integer quantity,
        String status
) {
}
