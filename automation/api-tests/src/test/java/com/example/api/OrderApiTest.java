package com.example.api;

import com.example.api.config.ApiConfig;
import io.restassured.RestAssured;
import org.testng.annotations.Test;

import static org.hamcrest.Matchers.equalTo;

public class OrderApiTest extends BaseApiTest {

    @Test
    public void shouldGetOrderById() {

        request
                .when()
                .get("/orders/ORD-1001")
                .then()
                .statusCode(200)
                .body("id", equalTo("ORD-1001"))
                .body("customerName", equalTo("Rahul"))
                .body("product", equalTo("Laptop"))
                .body("quantity", equalTo(1))
                .body("status", equalTo("CREATED"));
    }

    @Test
    public void shouldGetOrderWithInventory() {

        request
                .when()
                .get("/orders/ORD-1001/inventory")
                .then()
                .statusCode(200)
                .body("order.id", equalTo("ORD-1001"))
                .body("order.product", equalTo("Laptop"))
                .body("order.quantity", equalTo(1))
                .body("inventory.product", equalTo("Laptop"))
                .body("inventory.availableQuantity", equalTo(10))
                .body("inventory.status", equalTo("AVAILABLE"));
    }

    @Test
    public void shouldReturn404ForUnknownOrder() {

        request
                .when()
                .get("/orders/ORD-9999")
                .then()
                .statusCode(404);
    }

    @Test
    public void shouldPublishOrderCreatedEvent() {
        request
                .when()
                .post("/orders/ORD-1001/events")
                .then()
                .statusCode(200)
                .body("message", equalTo("ORDER_CREATED event published"))
                .body("orderId", equalTo("ORD-1001"));
    }

    @Test
    public void shouldReturn401WithoutAuthentication() {

        RestAssured
                .given()
                .baseUri(ApiConfig.baseUrl())
                .when()
                .get("/orders/ORD-1001")
                .then()
                .statusCode(401);
    }
}
