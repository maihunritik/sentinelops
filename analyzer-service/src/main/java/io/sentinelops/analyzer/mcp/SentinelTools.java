package io.sentinelops.analyzer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.sentinelops.analyzer.detection.AnomalyDetector;
import io.sentinelops.analyzer.incident.IncidentService;
import io.sentinelops.analyzer.incident.IncidentStatus;
import io.sentinelops.analyzer.incident.IncidentView;
import io.sentinelops.analyzer.runbook.RunbookIndex;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The tools an AI agent can use to triage incidents: read incidents and live service health,
 * pull relevant runbooks for grounding, and acknowledge an incident once a human or agent owns it.
 */
@Component
public class SentinelTools {

    private final List<McpTool> tools;

    public SentinelTools(IncidentService incidents, AnomalyDetector detector, RunbookIndex runbooks) {
        this.tools = List.of(
                new McpTool("list_incidents", "List incidents",
                        "List incidents, newest first. Defaults to active ones (OPEN and ACKNOWLEDGED).",
                        schema(Map.of(
                                "status", Map.of("type", "string", "enum", List.of("OPEN", "ACKNOWLEDGED", "RESOLVED", "ACTIVE", "ALL"),
                                        "description", "Filter by status. ACTIVE = OPEN + ACKNOWLEDGED."),
                                "service", Map.of("type", "string", "description", "Only incidents for this service"),
                                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)), List.of()),
                        args -> incidents.list(statuses(text(args, "status", "ACTIVE")), text(args, "service", null),
                                args.path("limit").asInt(20))),

                new McpTool("get_incident", "Get incident",
                        "Full details of one incident plus the runbooks most relevant to it.",
                        schema(Map.of("id", Map.of("type", "integer")), List.of("id")),
                        args -> {
                            IncidentView incident = incidents.get(requiredLong(args, "id"));
                            String query = incident.service() + " " + incident.type().name().replace('_', ' ');
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.put("incident", incident);
                            out.put("suggestedRunbooks", runbooks.search(query, 3));
                            return out;
                        }),

                new McpTool("get_service_health", "Get service health",
                        "Live sliding-window metrics (request count, error rate, p50/p95/p99 latency, learned baseline) "
                                + "for one service or for every service seen recently.",
                        schema(Map.of("service", Map.of("type", "string")), List.of()),
                        args -> {
                            String service = text(args, "service", null);
                            List<String> names = service == null ? detector.knownServices() : List.of(service);
                            return names.stream().flatMap(s -> detector.peek(s).stream()).toList();
                        }),

                new McpTool("search_runbooks", "Search runbooks",
                        "Keyword search over the team's remediation runbooks. Returns ranked matches with excerpts.",
                        schema(Map.of("query", Map.of("type", "string"),
                                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 10)), List.of("query")),
                        args -> runbooks.search(requiredText(args, "query"), args.path("limit").asInt(3))),

                new McpTool("get_runbook", "Get runbook",
                        "Full markdown text of one runbook by id (ids come from search_runbooks or get_incident).",
                        schema(Map.of("id", Map.of("type", "string")), List.of("id")),
                        args -> {
                            String id = requiredText(args, "id");
                            return runbooks.get(id).orElseThrow(
                                    () -> new McpTool.ToolArgumentException("No runbook with id " + id));
                        }),

                new McpTool("acknowledge_incident", "Acknowledge incident",
                        "Mark an incident as acknowledged so others know it is being handled. Changes state.",
                        schema(Map.of("id", Map.of("type", "integer"),
                                "acknowledgedBy", Map.of("type", "string", "description", "Who is taking ownership"),
                                "note", Map.of("type", "string", "description", "Findings or next steps")),
                                List.of("id", "acknowledgedBy")),
                        args -> incidents.acknowledge(requiredLong(args, "id"), requiredText(args, "acknowledgedBy"),
                                text(args, "note", null)))
        );
    }

    public List<McpTool> all() {
        return tools;
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", properties);
        s.put("required", required);
        s.put("additionalProperties", false);
        return s;
    }

    private static Set<IncidentStatus> statuses(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> IncidentStatus.ACTIVE;
            case "ALL" -> EnumSet.allOf(IncidentStatus.class);
            default -> {
                try {
                    yield EnumSet.of(IncidentStatus.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new McpTool.ToolArgumentException("Unknown status " + value);
                }
            }
        };
    }

    private static String text(JsonNode args, String field, String fallback) {
        JsonNode n = args.get(field);
        return n == null || n.isNull() || n.asText().isBlank() ? fallback : n.asText();
    }

    private static String requiredText(JsonNode args, String field) {
        String v = text(args, field, null);
        if (v == null) {
            throw new McpTool.ToolArgumentException("'" + field + "' is required");
        }
        return v;
    }

    private static long requiredLong(JsonNode args, String field) {
        JsonNode n = args.get(field);
        if (n == null || !n.canConvertToLong()) {
            throw new McpTool.ToolArgumentException("'" + field + "' must be an integer");
        }
        return n.asLong();
    }
}
