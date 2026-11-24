package io.sentinelops.analyzer.mcp;

import io.sentinelops.analyzer.detection.AnomalyDetector;
import io.sentinelops.analyzer.detection.AnomalyType;
import io.sentinelops.analyzer.incident.IncidentNotFoundException;
import io.sentinelops.analyzer.incident.IncidentService;
import io.sentinelops.analyzer.incident.IncidentStatus;
import io.sentinelops.analyzer.incident.IncidentView;
import io.sentinelops.analyzer.incident.Severity;
import io.sentinelops.analyzer.runbook.RunbookIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(McpController.class)
@Import({SentinelTools.class, RunbookIndex.class})
@TestPropertySource(properties = "sentinel.mcp.token=s3cret")
class McpControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    IncidentService incidents;

    @MockitoBean
    AnomalyDetector detector;

    @Test
    void initializeNegotiatesVersionAndAdvertisesTools() throws Exception {
        rpc("""
                {"jsonrpc":"2.0","id":1,"method":"initialize",
                 "params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-03-26"))
                .andExpect(jsonPath("$.result.capabilities.tools").exists())
                .andExpect(jsonPath("$.result.serverInfo.name").value("sentinelops"));
    }

    @Test
    void unknownProtocolVersionFallsBackToLatest() throws Exception {
        rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}")
                .andExpect(jsonPath("$.result.protocolVersion").value(McpController.SUPPORTED_VERSIONS.getFirst()));
    }

    @Test
    void notificationsAreAcceptedWithoutBody() throws Exception {
        rpc("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").andExpect(status().isAccepted());
    }

    @Test
    void listsTools() throws Exception {
        rpc("{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"tools/list\"}")
                .andExpect(jsonPath("$.result.tools[*].name", hasItems(
                        "list_incidents", "get_incident", "get_service_health", "search_runbooks",
                        "get_runbook", "acknowledge_incident")))
                .andExpect(jsonPath("$.result.tools[0].inputSchema.type").value("object"));
    }

    @Test
    void getIncidentReturnsDetailsAndSuggestedRunbooks() throws Exception {
        when(incidents.get(7L)).thenReturn(new IncidentView(7, "payments", AnomalyType.ERROR_RATE_SPIKE,
                Severity.CRITICAL, IncidentStatus.OPEN, "payments: 60% failed", Instant.EPOCH, Instant.EPOCH,
                null, null, null, 0.6, 120, 90L));

        rpc(call("get_incident", "{\"id\":7}"))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text", containsString("\"severity\" : \"CRITICAL\"")))
                .andExpect(jsonPath("$.result.content[0].text", containsString("high-error-rate")));
    }

    @Test
    void toolFailuresAreReportedAsToolErrorsNotProtocolErrors() throws Exception {
        when(incidents.get(99L)).thenThrow(new IncidentNotFoundException(99));

        rpc(call("get_incident", "{\"id\":99}"))
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.content[0].text").value("Incident 99 not found"));

        rpc(call("get_incident", "{}"))
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.content[0].text").value("'id' must be an integer"));
    }

    @Test
    void protocolErrors() throws Exception {
        rpc(call("drop_database", "{}")).andExpect(jsonPath("$.error.code").value(-32602));
        rpc("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/list\"}").andExpect(jsonPath("$.error.code").value(-32601));
        rpc("{not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(-32700));
        rpc("{\"id\":1,\"method\":\"ping\"}").andExpect(jsonPath("$.error.code").value(-32600));
    }

    @Test
    void requiresBearerToken() throws Exception {
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer wrong")
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getIsNotSupported() throws Exception {
        mvc.perform(get("/mcp").header("Authorization", "Bearer s3cret")).andExpect(status().isMethodNotAllowed());
    }

    private static String call(String tool, String args) {
        return "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}"
                .formatted(tool, args);
    }

    private ResultActions rpc(String body) throws Exception {
        return mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer s3cret").content(body));
    }
}
