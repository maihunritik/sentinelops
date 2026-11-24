package io.sentinelops.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinelops.analyzer.detection.AnomalyDetector;
import io.sentinelops.analyzer.incident.IncidentService;
import io.sentinelops.common.TelemetryEvent;
import io.sentinelops.common.Topics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full path: telemetry on Kafka -> detector -> incident in the database -> incident event on Kafka
 * -> an agent triages and acknowledges it over MCP -> traffic recovers -> incident auto-resolves.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {Topics.TELEMETRY, Topics.INCIDENTS})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndToEndTest {

    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired MutableClock clock;
    @Autowired AnomalyDetector detector;
    @Autowired IncidentService incidents;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void detectsTriagesAndResolvesAnIncident() throws Exception {
        KafkaTemplate<String, TelemetryEvent> producer = producer();
        try (Consumer<String, String> incidentFeed = incidentConsumer()) {

            // 1. payments starts failing: 15 of 25 requests return 503.
            sendTraffic(producer, "payments", 25, 15);
            sendTraffic(producer, "search", 25, 0);
            await().atMost(Duration.ofSeconds(20)).until(() ->
                    detector.peek("payments").map(a -> a.stats().requests() == 25).orElse(false)
                            && detector.peek("search").map(a -> a.stats().requests() == 25).orElse(false));
            incidents.reconcile(detector.evaluate(clock.instant()));

            // 2. An incident event goes out on Kafka after commit.
            ConsumerRecord<String, String> opened = KafkaTestUtils.getSingleRecord(incidentFeed, Topics.INCIDENTS);
            assertThat(opened.key()).isEqualTo("payments");
            assertThat(opened.value()).contains("\"status\":\"OPEN\"", "\"severity\":\"CRITICAL\"");

            // 3. An agent finds it through MCP and gets runbooks with it.
            JsonNode listed = callTool("list_incidents", Map.of());
            assertThat(listed).hasSize(1);
            long id = listed.get(0).get("id").asLong();
            assertThat(listed.get(0).get("type").asText()).isEqualTo("ERROR_RATE_SPIKE");

            JsonNode detail = callTool("get_incident", Map.of("id", id));
            assertThat(detail.at("/suggestedRunbooks/0/id").asText()).isIn("payments-service", "high-error-rate");

            JsonNode acked = callTool("acknowledge_incident",
                    Map.of("id", id, "acknowledgedBy", "triage-agent", "note", "Gateway timeouts, rolling back"));
            assertThat(acked.get("status").asText()).isEqualTo("ACKNOWLEDGED");

            // 4. Traffic recovers; after two healthy evaluations the incident resolves itself.
            clock.advance(Duration.ofMinutes(2));
            sendTraffic(producer, "payments", 25, 0);
            await().atMost(Duration.ofSeconds(20)).until(() ->
                    detector.peek("payments").map(a -> a.stats().requests() == 25).orElse(false));
            incidents.reconcile(detector.evaluate(clock.instant()));
            assertThat(callTool("list_incidents", Map.of())).hasSize(1);   // one healthy window is not enough
            incidents.reconcile(detector.evaluate(clock.instant()));
            assertThat(callTool("list_incidents", Map.of())).isEmpty();
            assertThat(callTool("list_incidents", Map.of("status", "RESOLVED")).get(0).get("note").asText())
                    .isEqualTo("Gateway timeouts, rolling back");

            List<String> feed = new ArrayList<>();
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                incidentFeed.poll(Duration.ofMillis(200)).forEach(r -> feed.add(r.value()));
                return feed.stream().anyMatch(v -> v.contains("\"status\":\"RESOLVED\""));
            });
            assertThat(feed).anyMatch(v -> v.contains("\"status\":\"ACKNOWLEDGED\""));
            assertThat(opened.value()).containsPattern("\"at\":\"\\d{4}-\\d{2}-\\d{2}T");
        }
    }

    private void sendTraffic(KafkaTemplate<String, TelemetryEvent> producer, String service, int total, int errors) {
        for (int i = 0; i < total; i++) {
            producer.send(Topics.TELEMETRY, service, new TelemetryEvent(UUID.randomUUID().toString(), service,
                    "POST /x", i < errors ? 503 : 200, 80 + i, clock.instant()));
        }
        producer.flush();
    }

    private JsonNode callTool(String name, Map<String, Object> args) throws Exception {
        String body = mapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call",
                "params", Map.of("name", name, "arguments", args)));
        String response = mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = mapper.readTree(response).get("result");
        assertThat(result.get("isError").asBoolean()).as(response).isFalse();
        return mapper.readTree(result.at("/content/0/text").asText());
    }

    private KafkaTemplate<String, TelemetryEvent> producer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put("key.serializer", StringSerializer.class);
        props.put("value.serializer", JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private Consumer<String, String> incidentConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("e2e-test", "false", broker);
        props.put("auto.offset.reset", "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, Topics.INCIDENTS);
        return consumer;
    }
}
