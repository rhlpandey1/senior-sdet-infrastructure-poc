package com.example.order.repository;

import com.example.order.exception.OrderNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> findById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, customer_name, product, quantity, status " +
                    "FROM orders WHERE id = ?",
                    id
            );
        } catch (Exception e) {
            throw new OrderNotFoundException("Order not found: " + id);
        }
    }

    public Map<String, Object> checkDatabase() {
        return jdbcTemplate.queryForMap(
                "SELECT current_database(), current_user"
        );
    }
}
