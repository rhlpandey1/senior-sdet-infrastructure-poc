package com.example.order.dto;

public record InventoryResponse(
        String product,
        Integer availableQuantity,
        String status
) {
}
