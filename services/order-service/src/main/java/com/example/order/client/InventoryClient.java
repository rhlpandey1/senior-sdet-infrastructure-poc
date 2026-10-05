package com.example.order.client;

import com.example.order.dto.InventoryResponse;
import com.example.order.exception.InventoryServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class InventoryClient {

    private final RestClient restClient;
    private final String username;
    private final String password;

    public InventoryClient(
            @Value("${inventory.service.url}") String inventoryServiceUrl,
            @Value("${security.username}") String username,
            @Value("${security.password}") String password) {

        this.restClient = RestClient.builder()
                .baseUrl(inventoryServiceUrl)
                .build();

        this.username = username;
        this.password = password;
    }

    public InventoryResponse getInventory(String product) {

        try {
            String credentials = username + ":" + password;

            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(
                            credentials.getBytes(StandardCharsets.UTF_8)
                    );

            return restClient.get()
                    .uri("/inventory/{product}", product)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Basic " + encodedCredentials
                    )
                    .retrieve()
                    .body(InventoryResponse.class);

        } catch (Exception exception) {
            throw new InventoryServiceException(
                    "Unable to retrieve inventory for product: " + product,
                    exception
            );
        }
    }
}
