package io.sentinelops.analyzer.config;

import io.sentinelops.common.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic incidentsTopic(@Value("${sentinel.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(Topics.INCIDENTS).partitions(3).replicas(replicas).build();
    }

    /** Poison messages are retried briefly, then logged and skipped so one bad event cannot stall a partition. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(500L, 2));
    }
}
