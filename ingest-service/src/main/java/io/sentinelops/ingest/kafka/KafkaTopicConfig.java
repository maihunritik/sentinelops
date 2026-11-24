package io.sentinelops.ingest.kafka;

import io.sentinelops.common.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic telemetryTopic(@Value("${sentinel.kafka.partitions:6}") int partitions,
                            @Value("${sentinel.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(Topics.TELEMETRY).partitions(partitions).replicas(replicas).build();
    }
}
