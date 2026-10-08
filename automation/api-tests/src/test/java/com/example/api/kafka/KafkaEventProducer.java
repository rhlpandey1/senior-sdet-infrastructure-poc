package com.example.api.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class KafkaEventProducer implements AutoCloseable {

    private final KafkaProducer<String, String> producer;

    public KafkaEventProducer() {
        Properties properties = new Properties();

        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KafkaConfig.bootstrapServers()
        );
        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );
        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );

        producer = new KafkaProducer<>(properties);
    }

    public void publish(String key, String eventJson) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        KafkaConfig.orderEventsTopic(),
                        key,
                        eventJson
                );

        producer.send(record);
        producer.flush();
    }

    @Override
    public void close() {
        producer.close();
    }
}
