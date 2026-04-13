package com.lyhn.coreworkflowjava.workflow.engine.integration.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class McpSseClient {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final String serverUrl;
    private final String serverId;
    private final OkHttpClient httpClient;
    private final AtomicInteger requestIdCounter = new AtomicInteger(0);
    private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());

    private volatile String messageEndpoint;
    private volatile boolean initialized = false;
    private volatile Call sseCall;

    private final Map<String, CompletableFuture<JSONObject>> pendingRequests = new ConcurrentHashMap<>();
    private final BlockingQueue<SseEvent> eventQueue = new LinkedBlockingQueue<>();

    private final ScheduledExecutorService scheduler;

    public McpSseClient(String serverId, String serverUrl, OkHttpClient httpClient) {
        this.serverId = serverId;
        this.serverUrl = serverUrl;
        this.httpClient = httpClient;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-sse-heartbeat-" + this.serverId);
            t.setDaemon(true);
            return t;
        });
    }

    public void connect() throws IOException {
        log.info("[McpSseClient] Connecting to MCP server: id={}, url={}", serverId, serverUrl);

        Request request = new Request.Builder()
                .url(serverUrl)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .get()
                .build();

        sseCall = httpClient.newCall(request);
        Response response = sseCall.execute();

        if (!response.isSuccessful()) {
            throw new IOException("Failed to connect to MCP server: " + response.code());
        }

        if (response.body() == null) {
            throw new IOException("MCP server returned empty response body");
        }

        Thread eventThread = new Thread(() -> readSseStream(response), "mcp-sse-reader-" + serverId);
        eventThread.setDaemon(true);
        eventThread.start();

        waitForEndpoint(30, TimeUnit.SECONDS);

        initialize();

        scheduler.scheduleAtFixedRate(this::checkHealth, 30, 30, TimeUnit.SECONDS);

        log.info("[McpSseClient] Connected to MCP server: id={}", serverId);
    }

    private void readSseStream(Response response) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body().byteStream()))) {
            String eventName = null;
            StringBuilder dataBuilder = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                lastActivityTime.set(System.currentTimeMillis());

                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (dataBuilder.length() > 0) {
                        dataBuilder.append("\n");
                    }
                    dataBuilder.append(data);
                } else if (line.isEmpty() && dataBuilder.length() > 0) {
                    String eventData = dataBuilder.toString();
                    handleSseEvent(eventName, eventData);
                    eventName = null;
                    dataBuilder = new StringBuilder();
                }
            }
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                log.error("[McpSseClient] SSE stream error for server {}: {}", serverId, e.getMessage());
            }
        } finally {
            initialized = false;
            log.warn("[McpSseClient] SSE stream closed for server {}", serverId);
        }
    }

    private void handleSseEvent(String eventName, String data) {
        try {
            if ("endpoint".equals(eventName)) {
                messageEndpoint = data;
                log.info("[McpSseClient] Received endpoint for server {}: {}", serverId, data);
                return;
            }

            JSONObject jsonRpc = JSON.parseObject(data);
            String id = jsonRpc.getString("id");

            if (id != null && pendingRequests.containsKey(id)) {
                CompletableFuture<JSONObject> future = pendingRequests.remove(id);
                if (jsonRpc.containsKey("error")) {
                    JSONObject error = jsonRpc.getJSONObject("error");
                    future.completeExceptionally(
                            new RuntimeException("MCP error: " + error.getIntValue("code") + " - " + error.getString("message")));
                } else {
                    future.complete(jsonRpc.getJSONObject("result"));
                }
            } else if (jsonRpc.containsKey("method")) {
                log.debug("[McpSseClient] Received notification from server {}: {}", serverId, jsonRpc.getString("method"));
            }
        } catch (Exception e) {
            log.error("[McpSseClient] Error handling SSE event for server {}: {}", serverId, e.getMessage());
        }
    }

    private void waitForEndpoint(long timeout, TimeUnit unit) throws IOException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (messageEndpoint == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for endpoint");
            }
        }
        if (messageEndpoint == null) {
            throw new IOException("Timeout waiting for MCP server endpoint");
        }
    }

    private void initialize() throws IOException {
        JSONObject initParams = new JSONObject();
        JSONObject capabilities = new JSONObject();
        capabilities.put("roots", new JSONObject());
        capabilities.put("sampling", new JSONObject());
        initParams.put("capabilities", capabilities);
        initParams.put("clientInfo", new JSONObject()
                .fluentPut("name", "IntelligentFlow-MCP-Client")
                .fluentPut("version", "1.0.0"));

        try {
            JSONObject result = sendRequest("initialize", initParams);
            log.info("[McpSseClient] Initialized with server {}: {}", serverId,
                    result != null ? result.getJSONObject("serverInfo") : "null");

            sendNotification("notifications/initialized", new JSONObject());
            initialized = true;
        } catch (Exception e) {
            throw new IOException("Failed to initialize MCP connection: " + e.getMessage(), e);
        }
    }

    public List<McpToolDefinition> listTools() throws IOException {
        ensureInitialized();

        try {
            JSONObject result = sendRequest("tools/list", new JSONObject());
            List<McpToolDefinition> tools = new ArrayList<>();

            if (result != null && result.containsKey("tools")) {
                List<JSONObject> toolArray = result.getJSONArray("tools").toJavaList(JSONObject.class);
                for (JSONObject toolJson : toolArray) {
                    McpToolDefinition tool = McpToolDefinition.builder()
                            .name(toolJson.getString("name"))
                            .description(toolJson.getString("description"))
                            .inputSchema(toolJson.getJSONObject("inputSchema"))
                            .serverId(serverId)
                            .serverUrl(serverUrl)
                            .transportType(McpToolDefinition.McpTransportType.SSE)
                            .enabled(true)
                            .build();
                    tools.add(tool);
                }
            }

            log.info("[McpSseClient] Listed {} tools from server {}", tools.size(), serverId);
            return tools;
        } catch (Exception e) {
            throw new IOException("Failed to list tools from MCP server: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) throws IOException {
        ensureInitialized();

        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : new HashMap<>());

        try {
            JSONObject result = sendRequest("tools/call", params);

            if (result != null && result.containsKey("content")) {
                List<JSONObject> contentArray = result.getJSONArray("content").toJavaList(JSONObject.class);
                Map<String, Object> output = new LinkedHashMap<>();
                StringBuilder textBuilder = new StringBuilder();

                for (JSONObject content : contentArray) {
                    String type = content.getString("type");
                    if ("text".equals(type)) {
                        textBuilder.append(content.getString("text"));
                    } else {
                        output.put(type, content);
                    }
                }

                if (textBuilder.length() > 0) {
                    output.put("text", textBuilder.toString());
                }

                output.put("isError", result.getBooleanValue("isError"));

                log.info("[McpSseClient] Tool '{}' executed on server {}", toolName, serverId);
                return output;
            }

            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            throw new IOException("Failed to call tool '" + toolName + "': " + e.getMessage(), e);
        }
    }

    private JSONObject sendRequest(String method, JSONObject params) throws IOException {
        String id = String.valueOf(requestIdCounter.incrementAndGet());
        JSONObject request = new JSONObject();
        request.put("jsonrpc", JSON_RPC_VERSION);
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);

        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        String fullEndpoint = resolveEndpoint();
        RequestBody body = RequestBody.create(request.toJSONString(), JSON_MEDIA_TYPE);

        Request httpRequest = new Request.Builder()
                .url(fullEndpoint)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                pendingRequests.remove(id);
                throw new IOException("MCP request failed with status: " + response.code());
            }

            if (response.body() != null) {
                String responseBody = response.body().string();
                try {
                    JSONObject jsonResponse = JSON.parseObject(responseBody);
                    if (jsonResponse.containsKey("error")) {
                        pendingRequests.remove(id);
                        JSONObject error = jsonResponse.getJSONObject("error");
                        throw new IOException("MCP error: " + error.getIntValue("code") + " - " + error.getString("message"));
                    }
                    if (jsonResponse.containsKey("result")) {
                        pendingRequests.remove(id);
                        return jsonResponse.getJSONObject("result");
                    }
                } catch (Exception parseEx) {
                    // Not a direct JSON-RPC response, wait for SSE event
                }
            }
        }

        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(id);
            throw new IOException("MCP request timeout for method: " + method);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingRequests.remove(id);
            throw new IOException("MCP request interrupted");
        } catch (ExecutionException e) {
            pendingRequests.remove(id);
            throw new IOException("MCP request failed: " + e.getCause().getMessage());
        }
    }

    private void sendNotification(String method, JSONObject params) throws IOException {
        JSONObject notification = new JSONObject();
        notification.put("jsonrpc", JSON_RPC_VERSION);
        notification.put("method", method);
        notification.put("params", params);

        String fullEndpoint = resolveEndpoint();
        RequestBody body = RequestBody.create(notification.toJSONString(), JSON_MEDIA_TYPE);

        Request httpRequest = new Request.Builder()
                .url(fullEndpoint)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[McpSseClient] Notification failed: method={}, status={}", method, response.code());
            }
        }
    }

    private String resolveEndpoint() {
        if (messageEndpoint == null) {
            return serverUrl;
        }
        if (messageEndpoint.startsWith("http://") || messageEndpoint.startsWith("https://")) {
            return messageEndpoint;
        }
        try {
            java.net.URL base = new java.net.URL(serverUrl);
            String basePath = base.getPath();
            String resolvedPath;
            if (messageEndpoint.startsWith("/")) {
                resolvedPath = messageEndpoint;
            } else {
                resolvedPath = basePath.endsWith("/") ? basePath + messageEndpoint : basePath + "/" + messageEndpoint;
            }
            return new java.net.URL(base.getProtocol(), base.getHost(), base.getPort(), resolvedPath).toString();
        } catch (Exception e) {
            return serverUrl;
        }
    }

    private void ensureInitialized() throws IOException {
        if (!initialized) {
            throw new IOException("MCP client is not initialized for server: " + serverId);
        }
    }

    private void checkHealth() {
        long elapsed = System.currentTimeMillis() - lastActivityTime.get();
        if (elapsed > 300_000) {
            log.warn("[McpSseClient] No activity for {}ms on server {}, connection may be stale",
                    elapsed, serverId);
        }
    }

    public void disconnect() {
        log.info("[McpSseClient] Disconnecting from MCP server: {}", serverId);
        initialized = false;
        scheduler.shutdownNow();

        if (sseCall != null) {
            sseCall.cancel();
        }

        for (Map.Entry<String, CompletableFuture<JSONObject>> entry : pendingRequests.entrySet()) {
            entry.getValue().cancel(true);
        }
        pendingRequests.clear();

        log.info("[McpSseClient] Disconnected from MCP server: {}", serverId);
    }

    public boolean isConnected() {
        return initialized;
    }

    public long getLastActivityTime() {
        return lastActivityTime.get();
    }

    public String getServerId() {
        return serverId;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    private static class SseEvent {
        final String event;
        final String data;

        SseEvent(String event, String data) {
            this.event = event;
            this.data = data;
        }
    }
}
