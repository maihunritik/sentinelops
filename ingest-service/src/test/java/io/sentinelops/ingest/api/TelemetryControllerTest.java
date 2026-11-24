package io.sentinelops.ingest.api;

import io.sentinelops.common.TelemetryEvent;
import io.sentinelops.ingest.dedup.DedupStore;
import io.sentinelops.ingest.kafka.TelemetryPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
class TelemetryControllerTest {

    private static final String EVENT = """
            {"eventId":"%s","service":"payments","endpoint":"POST /pay",
             "statusCode":200,"latencyMs":42,"timestamp":"2026-11-24T10:00:00Z"}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DedupStore dedup;

    @MockitoBean
    TelemetryPublisher publisher;

    @Test
    void acceptsAndPublishesNewEvent() throws Exception {
        when(dedup.markIfFirstSeen("e-1")).thenReturn(true);
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

        mvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(EVENT.formatted("e-1")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicates").value(0));

        verify(publisher).publish(any(TelemetryEvent.class));
    }

    @Test
    void skipsDuplicatesInBatch() throws Exception {
        when(dedup.markIfFirstSeen(eq("a"))).thenReturn(true);
        when(dedup.markIfFirstSeen(eq("b"))).thenReturn(false);
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

        String body = "[" + EVENT.formatted("a") + "," + EVENT.formatted("b") + "]";
        mvc.perform(post("/api/v1/telemetry/batch").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicates").value(1));

        verify(publisher, times(1)).publish(any());
    }

    @Test
    void rejectsInvalidEvent() throws Exception {
        String invalid = """
                {"eventId":"","service":"payments","statusCode":42,"latencyMs":-1}""";

        mvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());

        verify(dedup, never()).markIfFirstSeen(anyString());
    }

    @Test
    void rejectsInvalidEventInsideBatch() throws Exception {
        String body = "[" + EVENT.formatted("ok") + ",{\"eventId\":\"x\",\"service\":\"\",\"statusCode\":200,"
                + "\"latencyMs\":1,\"timestamp\":\"2026-11-24T10:00:00Z\"}]";

        mvc.perform(post("/api/v1/telemetry/batch").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns503WhenKafkaIsDown() throws Exception {
        when(dedup.markIfFirstSeen("e-2")).thenReturn(true);
        when(publisher.publish(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        mvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(EVENT.formatted("e-2")))
                .andExpect(status().isServiceUnavailable());
    }
}
