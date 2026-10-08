package com.example.api.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;

public class KafkaEventConsumer implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;

    public KafkaEventConsumer(String groupId) {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KafkaConfig.bootstrapServers()
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                groupId
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "latest"
        );
        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                "false"
        );

        consumer = new KafkaConsumer<>(properties);

        consumer.subscribe(
                Collections.singletonList(KafkaConfig.orderEventsTopic())
        );
    }

    public String waitForEvent(
            String expectedOrderId,
            Duration timeout) {

        AtomicReference<String> receivedEvent =
                new AtomicReference<>();

        await()
                .atMost(timeout)
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {

                    var records =
                            consumer.poll(Duration.ofMillis(100));

                    for (ConsumerRecord<String, String> record : records) {

                        if (expectedOrderId.equals(record.key())
                                && record.value().contains(expectedOrderId)) {

                            receivedEvent.set(record.value());
                            return true;
                        }
                    }

                    return false;
                });

        return receivedEvent.get();
    }

    @Override
    public void close() {
        consumer.close();
    }
}
