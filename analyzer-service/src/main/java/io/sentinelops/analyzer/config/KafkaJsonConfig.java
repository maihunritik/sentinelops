package io.sentinelops.analyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/** Serialises Kafka values with Spring's ObjectMapper so timestamps are ISO-8601 strings, not epoch numbers. */
@Configuration
public class KafkaJsonConfig {

    @Bean
    @SuppressWarnings("unchecked")
    DefaultKafkaProducerFactoryCustomizer jsonValueSerializer(ObjectMapper mapper) {
        return factory -> ((DefaultKafkaProducerFactory<Object, Object>) factory).setValueSerializerSupplier(() -> {
            JsonSerializer<Object> serializer = new JsonSerializer<>(mapper);
            serializer.setAddTypeInfo(false);
            return serializer;
        });
    }
}
