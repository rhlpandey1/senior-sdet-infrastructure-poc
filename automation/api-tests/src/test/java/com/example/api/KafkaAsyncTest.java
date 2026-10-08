package com.example.api;

import com.example.api.kafka.KafkaEventConsumer;
import com.example.api.kafka.KafkaEventProducer;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertTrue;

public class KafkaAsyncTest {

    @Test(enabled = false)
    public void shouldPublishAndConsumeOrderCreatedEvent() {

        String orderId = "TEST-" + System.currentTimeMillis();

        String eventJson = """
                {
                  "eventType": "ORDER_CREATED",
                  "orderId": "%s",
                  "product": "Laptop",
                  "quantity": 1
                }
                """.formatted(orderId);

        try (KafkaEventConsumer consumer =
                     new KafkaEventConsumer("api-automation-" + orderId);
             KafkaEventProducer producer =
                     new KafkaEventProducer()) {


            producer.publish(orderId, eventJson);

            String receivedEvent =
                    consumer.waitForEvent(
                            orderId,
                            Duration.ofSeconds(15)
                    );

            assertTrue(
                    receivedEvent.contains("\"eventType\":\"ORDER_CREATED\""),
                    "Expected ORDER_CREATED event"
            );

            assertTrue(
                    receivedEvent.contains("\"orderId\":\"" + orderId + "\""),
                    "Expected matching orderId"
            );
        }
    }
}
