package com.example.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OrderController {

    private final JdbcTemplate jdbcTemplate;

    public OrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
}
