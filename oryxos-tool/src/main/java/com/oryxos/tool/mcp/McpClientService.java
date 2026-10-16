package com.oryxos.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** MCP 客户端服务：连接 MCP Server，获取工具列表，转发工具调用。 支持 stdio 传输协议（SSE 传输在后续版本实现）。 */
@Component
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();

    /**
     * 连接到指定的 MCP Server。
     *
     * @param name 连接名称
     * @param transport 传输类型：stdio / sse
     * @param command stdio 模式的命令（如 "npx"）
     * @param args 命令参数
     * @param url SSE 模式的 URL
     */
    public void connect(
            String name, String transport, String command, List<String> args, String url) {
        if ("stdio".equalsIgnoreCase(transport)) {
            connectStdio(name, command, args);
        } else if ("sse".equalsIgnoreCase(transport)) {
            log.info("SSE transport for MCP server '{}' not yet implemented, skipping", name);
        } else {
            log.warn("Unknown MCP transport: {} for server {}", transport, name);
        }
    }

    /** 获取指定 MCP Server 的工具列表。 */
    public List<McpToolInfo> listTools(String serverName) {
        McpConnection conn = connections.get(serverName);
        if (conn == null) {
            log.warn("MCP server '{}' not connected", serverName);
            return List.of();
        }
        try {
            ObjectNode request = mapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", System.currentTimeMillis());
            request.put("method", "tools/list");
            request.putObject("params");

            String response = sendAndReceive(conn, request);
            return parseToolList(response);
        } catch (Exception e) {
            log.error("Failed to list tools for MCP server '{}': {}", serverName, e.getMessage());
            return List.of();
        }
    }

    /** 调用指定 MCP Server 上的工具。 */
    public String callTool(String serverName, String toolName, Map<String, Object> arguments) {
        McpConnection conn = connections.get(serverName);
        if (conn == null) {
            return "Error: MCP server '" + serverName + "' not connected";
        }
        try {
            ObjectNode request = mapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", System.currentTimeMillis());
            request.put("method", "tools/call");
            ObjectNode params = request.putObject("params");
            params.put("name", toolName);
            params.set("arguments", mapper.valueToTree(arguments));

            return sendAndReceive(conn, request);
        } catch (Exception e) {
            return "Error: MCP tool call failed: " + e.getMessage();
        }
    }

    /** 断开所有 MCP 连接。 */
    public void disconnectAll() {
        connections
                .values()
                .forEach(
                        conn -> {
                            try {
                                conn.process.destroyForcibly();
                                log.info("Disconnected MCP server: {}", conn.name);
                            } catch (Exception e) {
                                log.warn(
                                        "Error disconnecting MCP server {}: {}",
                                        conn.name,
                                        e.getMessage());
                            }
                        });
        connections.clear();
    }

    /** 获取所有已连接的 MCP Server 名称。 */
    public Set<String> getConnectedServers() {
        return Collections.unmodifiableSet(connections.keySet());
    }

    // --- Internal ---

    private void connectStdio(String name, String command, List<String> args) {
        try {
            List<String> cmdList = new ArrayList<>();
            cmdList.add(command);
            if (args != null) cmdList.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            McpConnection conn =
                    new McpConnection(
                            name,
                            process,
                            new BufferedWriter(
                                    new OutputStreamWriter(
                                            process.getOutputStream(), StandardCharsets.UTF_8)),
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream(), StandardCharsets.UTF_8)));
            connections.put(name, conn);

            // Send initialize
            ObjectNode initRequest = mapper.createObjectNode();
            initRequest.put("jsonrpc", "2.0");
            initRequest.put("id", 1);
            initRequest.put("method", "initialize");
            ObjectNode params = initRequest.putObject("params");
            params.put("protocolVersion", "2024-11-05");
            params.putObject("clientInfo").put("name", "oryxos").put("version", "1.0.0");
            params.putObject("capabilities");

            String response = sendAndReceive(conn, initRequest);
            log.info("Connected to MCP server '{}' via stdio: {}", name, truncate(response, 100));
        } catch (Exception e) {
            log.error("Failed to connect MCP server '{}' via stdio: {}", name, e.getMessage());
        }
    }

    private String sendAndReceive(McpConnection conn, ObjectNode request) throws IOException {
        String json = mapper.writeValueAsString(request);
        synchronized (conn) {
            conn.writer.write(json);
            conn.writer.newLine();
            conn.writer.flush();

            String line = conn.reader.readLine();
            if (line == null) {
                throw new IOException("MCP server closed connection");
            }
            return line;
        }
    }

    private List<McpToolInfo> parseToolList(String jsonResponse) {
        List<McpToolInfo> tools = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode result = root.get("result");
            if (result == null) return tools;
            JsonNode toolsNode = result.get("tools");
            if (toolsNode == null || !toolsNode.isArray()) return tools;

            for (JsonNode toolNode : toolsNode) {
                String name = toolNode.has("name") ? toolNode.get("name").asText() : "";
                String description =
                        toolNode.has("description") ? toolNode.get("description").asText() : "";
                JsonNode schema = toolNode.get("inputSchema");
                tools.add(
                        new McpToolInfo(
                                name, description, schema != null ? schema.toString() : "{}"));
            }
        } catch (Exception e) {
            log.error("Failed to parse MCP tool list: {}", e.getMessage());
        }
        return tools;
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /** MCP 连接信息。 */
    static class McpConnection {
        final String name;
        final Process process;
        final BufferedWriter writer;
        final BufferedReader reader;

        McpConnection(String name, Process process, BufferedWriter writer, BufferedReader reader) {
            this.name = name;
            this.process = process;
            this.writer = writer;
            this.reader = reader;
        }
    }

    /** MCP 工具信息。 */
    public record McpToolInfo(String name, String description, String inputSchemaJson) {}
}
