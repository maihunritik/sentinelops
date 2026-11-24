package io.sentinelops.analyzer.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentinelops.analyzer.incident.IncidentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model Context Protocol server over the Streamable HTTP transport (stateless, JSON responses).
 * Implements {@code initialize}, {@code ping}, {@code tools/list} and {@code tools/call}, which is
 * all an agent needs to discover and use the SentinelOps tools.
 */
@RestController
public class McpController {

    static final List<String> SUPPORTED_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    private final ObjectMapper mapper;
    private final String token;

    public McpController(SentinelTools sentinelTools, ObjectMapper mapper,
                         @Value("${sentinel.mcp.token:}") String token) {
        sentinelTools.all().forEach(t -> tools.put(t.name(), t));
        this.mapper = mapper;
        this.token = token;
    }

    @PostMapping(path = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handle(@RequestBody String body,
                                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .body(error(null, -32001, "Missing or invalid bearer token"));
        }

        JsonNode request;
        try {
            request = mapper.readTree(body);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(error(null, -32700, "Parse error"));
        }
        if (request == null || !request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.hasNonNull("method")) {
            return ResponseEntity.badRequest().body(error(request == null ? null : request.get("id"), -32600, "Invalid Request"));
        }

        JsonNode id = request.get("id");
        String method = request.get("method").asText();
        JsonNode params = request.path("params");

        if (id == null) {
            return ResponseEntity.accepted().build(); // notification, e.g. notifications/initialized
        }

        return ResponseEntity.ok(switch (method) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, Map.of());
            case "tools/list" -> result(id, Map.of("tools", tools.values().stream().map(this::describe).toList()));
            case "tools/call" -> callTool(id, params);
            default -> error(id, -32601, "Method not found: " + method);
        });
    }

    /** This server never initiates messages, so there is no SSE stream to open. */
    @GetMapping("/mcp")
    public ResponseEntity<Void> noStream() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }

    private Map<String, Object> initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText(SUPPORTED_VERSIONS.getFirst());
        String version = SUPPORTED_VERSIONS.contains(requested) ? requested : SUPPORTED_VERSIONS.getFirst();
        return Map.of(
                "protocolVersion", version,
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of("name", "sentinelops", "title", "SentinelOps", "version", "1.0.0"),
                "instructions", "Use list_incidents to see what is broken, get_incident for details and suggested "
                        + "runbooks, get_service_health for live metrics, and acknowledge_incident once you have "
                        + "a diagnosis.");
    }

    private Map<String, Object> describe(McpTool t) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", t.name());
        d.put("title", t.title());
        d.put("description", t.description());
        d.put("inputSchema", t.inputSchema());
        return d;
    }

    private ObjectNode callTool(JsonNode id, JsonNode params) {
        String name = params.path("name").asText();
        McpTool tool = tools.get(name);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        JsonNode args = params.path("arguments");
        if (!args.isObject()) {
            args = mapper.createObjectNode();
        }
        try {
            Object value = tool.handler().apply(args);
            String text = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("content", List.of(Map.of("type", "text", "text", text)));
            r.put("isError", false);
            return result(id, r);
        } catch (McpTool.ToolArgumentException | IncidentNotFoundException | IllegalStateException e) {
            return result(id, toolError(e.getMessage()));
        } catch (Exception e) {
            log.error("Tool {} failed", name, e);
            return result(id, toolError("Internal error while running " + name));
        }
    }

    private static Map<String, Object> toolError(String message) {
        return Map.of("content", List.of(Map.of("type", "text", "text", message)), "isError", true);
    }

    private ObjectNode result(JsonNode id, Object result) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        n.set("id", id);
        n.set("result", mapper.valueToTree(result));
        return n;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        n.set("id", id);
        n.putObject("error").put("code", code).put("message", message);
        return n;
    }

    private boolean authorized(String header) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String expected = "Bearer " + token;
        return header != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }
}
