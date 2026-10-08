package com.example.api.config;

public final class ApiConfig {

    private ApiConfig() {
    }

    public static String baseUrl() {
        return System.getProperty(
                "baseUrl",
                "http://localhost:8080"
        );
    }

    public static String username() {
        return System.getProperty(
                "api.username",
                "sdet"
        );
    }

    public static String password() {
        return System.getProperty(
                "api.password",
                "sdet123"
        );
    }
}
