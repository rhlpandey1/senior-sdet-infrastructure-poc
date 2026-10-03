package com.example.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InventoryController {

    @GetMapping("/inventory/{product}")
    public Map<String, Object> getInventory(@PathVariable String product) {
        return Map.of(
                "product", product,
                "availableQuantity", 10,
                "status", "AVAILABLE"
        );
    }
}
