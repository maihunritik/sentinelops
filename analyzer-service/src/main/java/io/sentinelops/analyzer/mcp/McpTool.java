package io.sentinelops.analyzer.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.function.Function;

/**
 * One tool exposed to MCP clients.
 *
 * @param inputSchema JSON Schema for the tool's arguments
 * @param handler     receives the {@code arguments} object and returns any JSON-serialisable value
 */
public record McpTool(String name, String title, String description, Map<String, Object> inputSchema,
                      Function<JsonNode, Object> handler) {

    /** Thrown by handlers for bad arguments; reported to the model as a tool error it can correct. */
    public static class ToolArgumentException extends RuntimeException {
        public ToolArgumentException(String message) {
            super(message);
        }
    }
}
