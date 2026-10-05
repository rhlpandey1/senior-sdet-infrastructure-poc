package com.example.inventory.dto;

public record InventoryResponse(
        String product,
        Integer availableQuantity,
        String status
) {
}
