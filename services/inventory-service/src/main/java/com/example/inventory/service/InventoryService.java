package com.example.inventory.service;

import com.example.inventory.dto.InventoryResponse;
import com.example.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public InventoryResponse getInventory(String product) {

        Map<String, Object> inventory =
                inventoryRepository.findByProduct(product);

        return new InventoryResponse(
                (String) inventory.get("product"),
                (Integer) inventory.get("available_quantity"),
                (String) inventory.get("status")
        );
    }
}
