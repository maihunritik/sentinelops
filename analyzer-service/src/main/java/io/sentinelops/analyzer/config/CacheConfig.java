package io.sentinelops.analyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinelops.analyzer.incident.IncidentService;
import io.sentinelops.analyzer.incident.IncidentView;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

@Configuration
public class CacheConfig {

    /** Stores incidents in Redis as readable JSON with a short TTL. Only used when spring.cache.type=redis. */
    @Bean
    RedisCacheManagerBuilderCustomizer incidentCache(ObjectMapper mapper) {
        var serializer = new Jackson2JsonRedisSerializer<>(mapper, IncidentView.class);
        return builder -> builder.withCacheConfiguration(IncidentService.CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .serializeValuesWith(SerializationPair.fromSerializer(serializer)));
    }
}
