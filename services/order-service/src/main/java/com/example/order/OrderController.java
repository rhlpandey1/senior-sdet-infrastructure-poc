package com.example.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@RestController
public class OrderController {

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;

    public OrderController(
            JdbcTemplate jdbcTemplate,
            @Value("${inventory.service.url}") String inventoryServiceUrl) {

        this.jdbcTemplate = jdbcTemplate;

        this.restClient = RestClient.builder()
                .baseUrl(inventoryServiceUrl)
                .build();
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrder(@PathVariable String id) {
        return jdbcTemplate.queryForMap(
                "SELECT id, customer_name, product, quantity, status " +
                "FROM orders WHERE id = ?",
                id
        );
    }

    @GetMapping("/db-check")
    public Map<String, Object> checkDatabase() {
        return jdbcTemplate.queryForMap(
                "SELECT current_database(), current_user"
        );
    }

    @GetMapping("/orders/{id}/inventory")
    public Map<String, Object> getOrderInventory(@PathVariable String id) {

        Map<String, Object> order = getOrder(id);

        String product = (String) order.get("product");

        Map<String, Object> inventory = restClient.get()
                .uri("/inventory/{product}", product)
                .retrieve()
                .body(Map.class);

        Map<String, Object> response = new HashMap<>();
        response.put("order", order);
        response.put("inventory", inventory);

        return response;
    }
}
