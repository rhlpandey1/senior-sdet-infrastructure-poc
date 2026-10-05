package com.example.inventory.repository;

import com.example.inventory.exception.InventoryNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class InventoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public InventoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> findByProduct(String product) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT product, available_quantity, status " +
                    "FROM inventory WHERE product = ?",
                    product
            );
        } catch (Exception e) {
            throw new InventoryNotFoundException(
                    "Inventory not found for product: " + product
            );
        }
    }
}
