package com.example.api.kafka;

public final class KafkaConfig {

    private KafkaConfig() {
    }

    public static String bootstrapServers() {
        return System.getProperty(
                "kafka.bootstrap.servers",
                "localhost:29092"
        );
    }

    public static String orderEventsTopic() {
        return System.getProperty(
                "kafka.order.topic",
                "order-events"
        );
    }
}
