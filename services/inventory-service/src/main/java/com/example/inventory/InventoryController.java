package com.example.inventory;

import com.example.inventory.dto.InventoryResponse;
import com.example.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{product}")
    public InventoryResponse getInventory(@PathVariable String product) {
        return inventoryService.getInventory(product);
    }
}
