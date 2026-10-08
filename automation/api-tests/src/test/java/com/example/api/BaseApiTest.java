package com.example.api;

import com.example.api.config.ApiConfig;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.BeforeClass;

public abstract class BaseApiTest {

    protected RequestSpecification request;

    @BeforeClass
    public void setup() {
        request = RestAssured
                .given()
                .baseUri(ApiConfig.baseUrl())
                .auth()
                .preemptive()
                .basic(ApiConfig.username(), ApiConfig.password())
                .contentType("application/json")
                .accept("application/json")
                .log()
                .ifValidationFails();
    }
}
