package com.oryxos.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于 Mem0 自托管 REST 服务的长期记忆存储。
 * 使用 JDK HttpClient 与 Jackson 序列化，遵守 LongTermMemoryStore 契约。
 */
public class Mem0MemoryStore implements LongTermMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(Mem0MemoryStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CORE_PAGE_SIZE = 100;

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final int archivalLimit;
    private final String userId;

    public Mem0MemoryStore(String baseUrl, String apiKey, int archivalLimit) {
        this(HttpClient.newHttpClient(), baseUrl, apiKey, archivalLimit, "oryxos-default");
    }

    public Mem0MemoryStore(String baseUrl, String apiKey, int archivalLimit, String userId) {
        this(HttpClient.newHttpClient(), baseUrl, apiKey, archivalLimit, userId);
    }

    public Mem0MemoryStore(HttpClient httpClient, String baseUrl, String apiKey,
                           int archivalLimit, String userId) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.archivalLimit = archivalLimit;
        this.userId = userId;
    }

    @Override
    public void append(String content, MemoryScope scope) {
        validate(content, scope);
        long start = System.nanoTime();
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", content));
        body.put("messages", messages);
        body.put("user_id", userId);
        body.put("metadata", Map.of("scope", scope.name()));

        HttpResponse<String> response = post("/v1/memories/", body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("Memory backend failure: backend=mem0, operation=append, statusCode={}",
                    response.statusCode());
            throw new MemoryStoreException("Mem0 append failed: HTTP " + response.statusCode()
                    + " body=" + response.body());
        }
        log.info("Memory append completed: backend=mem0, scope={}, resultCount=1, durationMs={}",
                scope, elapsedMillis(start));
    }

    @Override
    public MemoryContent load() {
        List<Mem0Entry> coreEntries = fetchByScope(MemoryScope.CORE, Integer.MAX_VALUE);
        List<Mem0Entry> archivalEntries = fetchByScope(MemoryScope.ARCHIVAL, archivalLimit);
        return new MemoryContent(join(coreEntries), join(archivalEntries));
    }

    @Override
    public List<String> recallByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Keyword must not be blank");
        }
        long start = System.nanoTime();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", keyword);
        body.put("user_id", userId);
        body.put("filters", Map.of("scope", MemoryScope.ARCHIVAL.name()));

        HttpResponse<String> response = post("/v1/memories/search/", body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("Memory backend failure: backend=mem0, operation=recall, statusCode={}",
                    response.statusCode());
            throw new MemoryStoreException("Mem0 recall failed: HTTP " + response.statusCode()
                    + " body=" + response.body());
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        List<String> results = parseEntries(response.body()).stream()
                .filter(entry -> entry.metadata() != null
                        && MemoryScope.ARCHIVAL.name().equals(entry.metadata().get("scope")))
                .filter(entry -> entry.content() != null
                        && entry.content().toLowerCase(Locale.ROOT).contains(lowerKeyword))
                .map(Mem0Entry::content)
                .toList();
        log.info("Memory recall completed: backend=mem0, scope=ARCHIVAL, resultCount={}, durationMs={}",
                results.size(), elapsedMillis(start));
        return results;
    }

    private List<Mem0Entry> fetchByScope(MemoryScope scope, int limit) {
        if (limit != Integer.MAX_VALUE) {
            return fetchPage(scope, limit, 1);
        }

        // CORE 不允许截断，必须持续读取到不足一页为止
        List<Mem0Entry> allEntries = new ArrayList<>();
        int page = 1;
        while (true) {
            List<Mem0Entry> entries = fetchPage(scope, CORE_PAGE_SIZE, page);
            allEntries.addAll(entries);
            if (entries.size() < CORE_PAGE_SIZE) {
                return allEntries;
            }
            page++;
        }
    }

    private List<Mem0Entry> fetchPage(MemoryScope scope, int limit, int page) {
        String url = baseUrl + "/v1/memories/?user_id=" + encode(userId)
                + "&metadata__scope=" + scope.name()
                + "&limit=" + limit
                + "&page=" + page;
        HttpResponse<String> response = get(url);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("Memory backend failure: backend=mem0, operation=load, statusCode={}",
                    response.statusCode());
            throw new MemoryStoreException("Mem0 load failed: HTTP " + response.statusCode()
                    + " body=" + response.body());
        }
        return parseEntries(response.body());
    }

    private HttpResponse<String> get(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Token " + apiKey);
        }
        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Memory backend failure: backend=mem0, operation=load, errorType={}",
                    e.getClass().getSimpleName());
            throw new MemoryStoreException("Mem0 request failed: GET " + url, e);
        }
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) {
        byte[] payload;
        try {
            payload = MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            log.error("Memory backend failure: backend=mem0, operation=serialize, errorType={}",
                    e.getClass().getSimpleName());
            throw new MemoryStoreException("Failed to serialize Mem0 request body", e);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Token " + apiKey);
        }
        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Memory backend failure: backend=mem0, operation=request, errorType={}",
                    e.getClass().getSimpleName());
            throw new MemoryStoreException("Mem0 request failed: POST " + path, e);
        }
    }

    private List<Mem0Entry> parseEntries(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Mem0Entry>>() {});
        } catch (IOException e) {
            log.error("Memory backend failure: backend=mem0, operation=parse, errorType={}",
                    e.getClass().getSimpleName());
            throw new MemoryStoreException("Failed to parse Mem0 response", e);
        }
    }

    private String join(List<Mem0Entry> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        return String.join("\n\n", entries.stream().map(Mem0Entry::content).toList());
    }

    private void validate(String content, MemoryScope scope) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Memory content must not be blank");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Memory scope must not be null");
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    record Mem0Entry(String id, String content, Map<String, Object> metadata) {
    }
}
