package com.example.order;

import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(
            @PathVariable
            @NotBlank String id) {

        return orderService.getOrder(id);
    }

    @GetMapping("/{id}/inventory")
    public OrderService.OrderInventoryResponse getOrderInventory(
            @PathVariable
            @NotBlank String id) {

        return orderService.getOrderInventory(id);
    }

    @GetMapping("/db-check")
    public Map<String, Object> checkDatabase() {
        return orderService.checkDatabase();
    }
@PostMapping("/{id}/events")
public Map<String, Object> publishOrderCreated(
        @PathVariable
        @NotBlank String id) {

    orderService.publishOrderCreated(id);

    return Map.of(
            "message", "ORDER_CREATED event published",
            "orderId", id
    );
}
}
