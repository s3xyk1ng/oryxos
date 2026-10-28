package com.oryxos.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mem0 后端契约测试，使用本地 HttpServer 模拟 Mem0 REST 接口。
 */
class Mem0MemoryStoreContractTest extends LongTermMemoryStoreContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private final List<Map<String, Object>> memories = new ArrayList<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    protected LongTermMemoryStore createStore() {
        return new Mem0MemoryStore(httpClient(), baseUrl(), "test-key", 200, "user-1");
    }

    @Override
    protected LongTermMemoryStore createAnotherStore() {
        return new Mem0MemoryStore(httpClient(), baseUrl(), "test-key", 200, "user-1");
    }

    @BeforeEach
    void setUp() throws IOException {
        memories.clear();
        idGenerator.set(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/memories/", this::handleRequest);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder().build();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @SuppressWarnings("unchecked")
    private void handleRequest(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        try {
            if ("POST".equals(method) && path.equals("/v1/memories/")) {
                Map<String, Object> body = MAPPER.readValue(exchange.getRequestBody(), new TypeReference<>() {});
                List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
                String content = messages.get(0).get("content");
                Map<String, Object> metadata = (Map<String, Object>) body.get("metadata");
                String scope = (String) metadata.get("scope");

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", String.valueOf(idGenerator.getAndIncrement()));
                entry.put("content", content);
                entry.put("metadata", Map.of("scope", scope));
                memories.add(entry);

                sendJson(exchange, 200, entry);
            } else if ("GET".equals(method) && path.equals("/v1/memories/")) {
                String scope = extractScope(query);
                int limit = extractLimit(query);
                List<Map<String, Object>> filtered = memories.stream()
                        .filter(m -> scope.equals(((Map<String, Object>) m.get("metadata")).get("scope")))
                        .toList();
                if ("CORE".equals(scope)) {
                    int page = extractPage(query);
                    int from = (page - 1) * limit;
                    if (from >= filtered.size()) {
                        filtered = List.of();
                    } else {
                        filtered = filtered.subList(from, Math.min(from + limit, filtered.size()));
                    }
                } else if (limit > 0 && limit < filtered.size()) {
                    filtered = filtered.subList(filtered.size() - limit, filtered.size());
                }
                sendJson(exchange, 200, filtered);
            } else if ("POST".equals(method) && path.equals("/v1/memories/search/")) {
                Map<String, Object> body = MAPPER.readValue(exchange.getRequestBody(), new TypeReference<>() {});
                // Fake Mem0 返回未过滤候选，验证 Java 侧仍执行 ARCHIVAL 与普通文本过滤
                sendJson(exchange, 200, memories);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } finally {
            exchange.close();
        }
    }

    private String extractScope(String query) {
        if (query == null) {
            return "";
        }
        for (String pair : query.split("&")) {
            if (pair.startsWith("metadata__scope=")) {
                return java.net.URLDecoder.decode(pair.substring("metadata__scope=".length()), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private int extractLimit(String query) {
        if (query == null) {
            return 0;
        }
        for (String pair : query.split("&")) {
            if (pair.startsWith("limit=")) {
                return Integer.parseInt(pair.substring("limit=".length()));
            }
        }
        return 0;
    }

    private int extractPage(String query) {
        if (query == null) {
            return 1;
        }
        for (String pair : query.split("&")) {
            if (pair.startsWith("page=")) {
                return Integer.parseInt(pair.substring("page=".length()));
            }
        }
        return 1;
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Test
    void append_preservesScopeMetadata() {
        Mem0MemoryStore store = (Mem0MemoryStore) createStore();
        store.append("core fact", MemoryScope.CORE);
        store.append("archival fact", MemoryScope.ARCHIVAL);

        assertThat(memories).hasSize(2);
        assertThat(memories.get(0).get("metadata")).isEqualTo(Map.of("scope", "CORE"));
        assertThat(memories.get(1).get("metadata")).isEqualTo(Map.of("scope", "ARCHIVAL"));
    }

    @Test
    void load_coreRetrievesAllPages() {
        for (int i = 0; i < 105; i++) {
            memories.add(Map.of(
                    "id", String.valueOf(i + 1),
                    "content", "CORE-PAGE-" + i,
                    "metadata", Map.of("scope", "CORE")));
        }

        MemoryContent loaded = createStore().load();

        assertThat(loaded.coreSection().split("\\n\\n")).hasSize(105);
        assertThat(loaded.coreSection()).contains("CORE-PAGE-0", "CORE-PAGE-104");
    }

    @Test
    void recall_appliesArchivalFilterBeforeReturning() {
        memories.add(Map.of("id", "core", "content", "shared-keyword core",
                "metadata", Map.of("scope", "CORE")));
        memories.add(Map.of("id", "archival", "content", "shared-keyword archival",
                "metadata", Map.of("scope", "ARCHIVAL")));
        memories.add(Map.of("id", "other", "content", "unrelated archival",
                "metadata", Map.of("scope", "ARCHIVAL")));

        assertThat(createStore().recallByKeyword("shared-keyword"))
                .containsExactly("shared-keyword archival");
    }

    @Test
    void load_withRemoteFailureThrowsMemoryStoreException() {
        server.stop(0);
        Mem0MemoryStore store = (Mem0MemoryStore) createStore();
        org.junit.jupiter.api.Assertions.assertThrows(MemoryStoreException.class,
                () -> store.load());
    }
}
