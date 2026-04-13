package com.lyhn.coreworkflowjava.workflow.engine.integration.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class McpModuleTest {

    @Nested
    @DisplayName("McpToolDefinition 测试")
    class McpToolDefinitionTest {

        @Test
        @DisplayName("创建 MCP 工具定义 - 基本属性")
        void testCreateToolDefinition() {
            Map<String, Object> inputSchema = new LinkedHashMap<>();
            inputSchema.put("type", "object");
            inputSchema.put("properties", Map.of(
                    "audio_url", Map.of("type", "string", "description", "音频URL")
            ));
            inputSchema.put("required", List.of("audio_url"));

            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("audio_to_text")
                    .description("语音听写工具")
                    .inputSchema(inputSchema)
                    .serverId("iat-server")
                    .serverUrl("http://localhost:8080/sse")
                    .serverName("实时语音听写")
                    .transportType(McpToolDefinition.McpTransportType.SSE)
                    .enabled(true)
                    .build();

            assertEquals("audio_to_text", tool.getName());
            assertEquals("语音听写工具", tool.getDescription());
            assertNotNull(tool.getInputSchema());
            assertEquals("iat-server", tool.getServerId());
            assertEquals("http://localhost:8080/sse", tool.getServerUrl());
            assertEquals(McpToolDefinition.McpTransportType.SSE, tool.getTransportType());
            assertTrue(tool.isEnabled());
        }

        @Test
        @DisplayName("McpToolDefinition - 必填字段校验")
        void testRequiredFieldsValidation() {
            McpToolDefinition validTool = McpToolDefinition.builder()
                    .name("test-tool")
                    .serverUrl("http://localhost:8080/sse")
                    .build();
            assertTrue(validTool.hasRequiredFields(), "Tool with name and URL should be valid");

            McpToolDefinition noName = McpToolDefinition.builder()
                    .serverUrl("http://localhost:8080/sse")
                    .build();
            assertFalse(noName.hasRequiredFields(), "Tool without name should be invalid");

            McpToolDefinition noUrl = McpToolDefinition.builder()
                    .name("test-tool")
                    .build();
            assertFalse(noUrl.hasRequiredFields(), "Tool without URL should be invalid");
        }

        @Test
        @DisplayName("McpToolDefinition - 默认值验证")
        void testDefaultValues() {
            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("test")
                    .serverUrl("http://localhost/sse")
                    .build();

            assertEquals(McpToolDefinition.McpTransportType.SSE, tool.getTransportType());
            assertTrue(tool.isEnabled());
        }

        @Test
        @DisplayName("McpToolDefinition - 传输类型枚举")
        void testTransportTypes() {
            assertEquals(2, McpToolDefinition.McpTransportType.values().length);
            assertEquals(McpToolDefinition.McpTransportType.SSE,
                    McpToolDefinition.McpTransportType.valueOf("SSE"));
            assertEquals(McpToolDefinition.McpTransportType.STREAMABLE_HTTP,
                    McpToolDefinition.McpTransportType.valueOf("STREAMABLE_HTTP"));
        }

        @Test
        @DisplayName("McpToolDefinition - 禁用工具")
        void testDisabledTool() {
            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("disabled-tool")
                    .serverUrl("http://localhost/sse")
                    .enabled(false)
                    .build();

            assertFalse(tool.isEnabled());
        }

        @Test
        @DisplayName("McpToolDefinition - 空名称校验")
        void testEmptyNameValidation() {
            McpToolDefinition emptyName = McpToolDefinition.builder()
                    .name("")
                    .serverUrl("http://localhost/sse")
                    .build();
            assertFalse(emptyName.hasRequiredFields());
        }
    }

    @Nested
    @DisplayName("McpServerConfig 测试")
    class McpServerConfigTest {

        @Test
        @DisplayName("创建 MCP 服务器配置")
        void testCreateServerConfig() {
            List<McpToolDefinition> tools = List.of(
                    McpToolDefinition.builder().name("tool1").serverUrl("http://localhost/sse").build(),
                    McpToolDefinition.builder().name("tool2").serverUrl("http://localhost/sse").build()
            );

            McpServerConfig config = McpServerConfig.builder()
                    .serverId("test-server")
                    .serverName("测试服务器")
                    .serverUrl("http://localhost:8080/sse")
                    .brief("测试用MCP服务器")
                    .transportType(McpToolDefinition.McpTransportType.SSE)
                    .authorized(true)
                    .tools(tools)
                    .maxConnections(3)
                    .connectionTimeoutMs(10000)
                    .readTimeoutMs(60000)
                    .idleTimeoutMs(120000)
                    .build();

            assertEquals("test-server", config.getServerId());
            assertEquals("测试服务器", config.getServerName());
            assertEquals(2, config.getTools().size());
            assertEquals(3, config.getMaxConnections());
            assertEquals(10000, config.getConnectionTimeoutMs());
            assertTrue(config.isAuthorized());
        }

        @Test
        @DisplayName("McpServerConfig - 连接池 Key 生成")
        void testConnectionPoolKey() {
            McpServerConfig config = McpServerConfig.builder()
                    .serverId("server-1")
                    .serverUrl("http://localhost:8080/sse")
                    .build();

            String key = config.getConnectionPoolKey();
            assertTrue(key.contains("server-1"));
            assertTrue(key.contains("http://localhost:8080/sse"));
        }

        @Test
        @DisplayName("McpServerConfig - 默认值验证")
        void testDefaultValues() {
            McpServerConfig config = McpServerConfig.builder().build();

            assertEquals(McpToolDefinition.McpTransportType.SSE, config.getTransportType());
            assertTrue(config.isAuthorized());
            assertEquals(5, config.getMaxConnections());
            assertEquals(30000, config.getConnectionTimeoutMs());
            assertEquals(300000, config.getReadTimeoutMs());
            assertEquals(300000, config.getIdleTimeoutMs());
        }

        @Test
        @DisplayName("McpServerConfig - 环境变量配置")
        void testEnvKeysConfig() {
            Map<String, String> envKeys = new LinkedHashMap<>();
            envKeys.put("API_KEY", "sk-xxx");
            envKeys.put("SECRET", "my-secret");

            McpServerConfig config = McpServerConfig.builder()
                    .serverId("test-server")
                    .serverUrl("http://localhost/sse")
                    .envKeys(envKeys)
                    .build();

            assertNotNull(config.getEnvKeys());
            assertEquals(2, config.getEnvKeys().size());
            assertEquals("sk-xxx", config.getEnvKeys().get("API_KEY"));
        }
    }

    @Nested
    @DisplayName("OkHttpClientPool 测试")
    class OkHttpClientPoolTest {

        private OkHttpClientPool pool;

        @BeforeEach
        void initPool() {
            pool = new OkHttpClientPool(10, 60000, 5000, 10000, 10000);
        }

        @AfterEach
        void cleanupPool() {
            if (pool != null) {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("OkHttpClientPool - 获取默认客户端")
        void testGetDefaultClient() {
            OkHttpClient client = pool.getClient();
            assertNotNull(client, "Default client should not be null");
        }

        @Test
        @DisplayName("OkHttpClientPool - 按 tag 获取客户端")
        void testGetClientByTag() {
            OkHttpClient client1 = pool.getClient("link-service");
            OkHttpClient client2 = pool.getClient("aitools-service");
            OkHttpClient client1Again = pool.getClient("link-service");

            assertNotNull(client1);
            assertNotNull(client2);
            assertSame(client1, client1Again, "Same tag should return same client instance");
        }

        @Test
        @DisplayName("OkHttpClientPool - 自定义超时客户端")
        void testGetClientWithTimeout() {
            OkHttpClient client = pool.getClientWithTimeout(5000, 15000);
            assertNotNull(client);
        }

        @Test
        @DisplayName("OkHttpClientPool - 连接池信息")
        void testPoolInfo() {
            OkHttpClientPool.PoolInfo info = pool.getPoolInfo();
            assertNotNull(info);
            assertEquals(10, info.maxIdleConnections());
            assertEquals(60000, info.keepAliveDurationMs());
            assertEquals(0, info.clientCount());
        }

        @Test
        @DisplayName("OkHttpClientPool - 默认构造器")
        void testDefaultConstructor() {
            OkHttpClientPool defaultPool = new OkHttpClientPool();
            try {
                OkHttpClient client = defaultPool.getClient();
                assertNotNull(client);
                OkHttpClientPool.PoolInfo info = defaultPool.getPoolInfo();
                assertEquals(20, info.maxIdleConnections());
            } finally {
                defaultPool.shutdown();
            }
        }

        @Test
        @DisplayName("OkHttpClientPool - 清理空闲连接")
        void testEvictAll() {
            pool.evictAll();
            OkHttpClientPool.PoolInfo info = pool.getPoolInfo();
            assertEquals(0, info.idleConnections());
        }

        @Test
        @DisplayName("OkHttpClientPool - 多次获取同一 tag 返回缓存实例")
        void testClientCacheConsistency() {
            OkHttpClient c1 = pool.getClient("test-tag");
            OkHttpClient c2 = pool.getClient("test-tag");
            OkHttpClient c3 = pool.getClient("test-tag");

            assertSame(c1, c2, "Should return cached instance");
            assertSame(c2, c3, "Should return cached instance");

            OkHttpClientPool.PoolInfo info = pool.getPoolInfo();
            assertEquals(1, info.clientCount());
        }

        @Test
        @DisplayName("OkHttpClientPool - 不同 tag 返回不同实例")
        void testDifferentTagsReturnDifferentInstances() {
            OkHttpClient c1 = pool.getClient("tag-1");
            OkHttpClient c2 = pool.getClient("tag-2");

            assertNotSame(c1, c2, "Different tags should return different instances");
            assertEquals(2, pool.getPoolInfo().clientCount());
        }
    }

    @Nested
    @DisplayName("McpConnectionPool 测试")
    class McpConnectionPoolTest {

        private McpConnectionPool pool;

        @BeforeEach
        void initPool() {
            pool = new McpConnectionPool(3, 5000, 10000, 30000, 60000);
        }

        @AfterEach
        void cleanupPool() {
            if (pool != null) {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("McpConnectionPool - 注册服务器")
        void testRegisterServer() {
            McpServerConfig config = McpServerConfig.builder()
                    .serverId("test-server")
                    .serverName("测试服务器")
                    .serverUrl("http://localhost:8080/sse")
                    .build();

            pool.registerServer(config);

            assertEquals(1, pool.getRegisteredServerCount());
            assertTrue(pool.getRegisteredServerIds().contains("test-server"));
        }

        @Test
        @DisplayName("McpConnectionPool - 注册多个服务器")
        void testRegisterMultipleServers() {
            for (int i = 0; i < 5; i++) {
                McpServerConfig config = McpServerConfig.builder()
                        .serverId("server-" + i)
                        .serverName("服务器" + i)
                        .serverUrl("http://localhost:" + (8080 + i) + "/sse")
                        .build();
                pool.registerServer(config);
            }

            assertEquals(5, pool.getRegisteredServerCount());
        }

        @Test
        @DisplayName("McpConnectionPool - 注销服务器")
        void testUnregisterServer() {
            McpServerConfig config = McpServerConfig.builder()
                    .serverId("test-server")
                    .serverName("测试服务器")
                    .serverUrl("http://localhost:8080/sse")
                    .build();

            pool.registerServer(config);
            assertEquals(1, pool.getRegisteredServerCount());

            pool.unregisterServer("test-server");
            assertEquals(0, pool.getRegisteredServerCount());
        }

        @Test
        @DisplayName("McpConnectionPool - 注销不存在的服务器")
        void testUnregisterNonExistentServer() {
            assertDoesNotThrow(() -> pool.unregisterServer("non-existent"));
        }

        @Test
        @DisplayName("McpConnectionPool - 获取连接统计")
        void testGetStats() {
            McpServerConfig config = McpServerConfig.builder()
                    .serverId("test-server")
                    .serverName("测试服务器")
                    .serverUrl("http://localhost:8080/sse")
                    .build();

            pool.registerServer(config);

            McpConnectionPool.PoolStats stats = pool.getStats("test-server");
            assertNotNull(stats);
            assertEquals(0, stats.activeConnections());
            assertEquals(0, stats.availableConnections());
            assertEquals(1, stats.totalServers());
        }

        @Test
        @DisplayName("McpConnectionPool - 注册空配置抛异常")
        void testRegisterNullConfig() {
            assertThrows(IllegalArgumentException.class, () -> pool.registerServer(null));
        }

        @Test
        @DisplayName("McpConnectionPool - 注册空URL抛异常")
        void testRegisterConfigWithNullUrl() {
            McpServerConfig config = McpServerConfig.builder()
                    .serverId("test")
                    .build();
            assertThrows(IllegalArgumentException.class, () -> pool.registerServer(config));
        }

        @Test
        @DisplayName("McpConnectionPool - 关闭后操作抛异常")
        void testOperationAfterShutdown() throws IOException {
            pool.shutdown();
            assertThrows(IllegalStateException.class, () -> pool.getConnection("test"));
        }

        @Test
        @DisplayName("McpConnectionPool - 默认构造器")
        void testDefaultConstructor() {
            McpConnectionPool defaultPool = new McpConnectionPool();
            try {
                assertNotNull(defaultPool);
                assertEquals(0, defaultPool.getRegisteredServerCount());
            } finally {
                defaultPool.shutdown();
            }
        }

        @Test
        @DisplayName("McpConnectionPool - 获取未注册服务器的统计")
        void testGetStatsForUnregisteredServer() {
            McpConnectionPool.PoolStats stats = pool.getStats("non-existent");
            assertNotNull(stats);
            assertEquals(0, stats.activeConnections());
            assertEquals(0, stats.availableConnections());
            assertEquals(0, stats.totalServers());
        }

        @Test
        @DisplayName("McpConnectionPool - 获取未注册服务器的连接抛异常")
        void testGetConnectionForUnregisteredServer() {
            assertThrows(IOException.class, () -> pool.getConnection("non-existent"));
        }
    }

    @Nested
    @DisplayName("McpToolExecutor 测试")
    class McpToolExecutorTest {

        private McpConnectionPool connectionPool;
        private McpToolExecutor executor;

        @BeforeEach
        void initExecutor() {
            connectionPool = new McpConnectionPool(3, 5000, 10000, 30000, 60000);
            executor = new McpToolExecutor(connectionPool);
        }

        @AfterEach
        void cleanupExecutor() {
            if (connectionPool != null) {
                connectionPool.shutdown();
            }
        }

        @Test
        @DisplayName("McpToolExecutor - 注册服务器并索引工具")
        void testRegisterServerWithTools() {
            List<McpToolDefinition> tools = List.of(
                    McpToolDefinition.builder().name("audio_to_text").description("语音听写")
                            .serverUrl("http://localhost/sse").build(),
                    McpToolDefinition.builder().name("text_translate").description("文本翻译")
                            .serverUrl("http://localhost/sse").build()
            );

            McpServerConfig config = McpServerConfig.builder()
                    .serverId("iat-server")
                    .serverName("语音听写服务")
                    .serverUrl("http://localhost:8080/sse")
                    .tools(tools)
                    .build();

            executor.registerServer(config);

            assertEquals(2, executor.getToolCount());
            assertEquals(1, executor.getServerCount());
        }

        @Test
        @DisplayName("McpToolExecutor - 注册服务器时自动设置 serverId 和 serverUrl")
        void testRegisterServerAutoSetFields() {
            List<McpToolDefinition> tools = List.of(
                    McpToolDefinition.builder().name("tool1").description("工具1")
                            .serverUrl("http://localhost/sse").build()
            );

            McpServerConfig config = McpServerConfig.builder()
                    .serverId("my-server")
                    .serverName("我的服务器")
                    .serverUrl("http://my-server:8080/sse")
                    .tools(tools)
                    .build();

            executor.registerServer(config);

            Optional<McpToolDefinition> toolOpt = executor.getToolDefinition("tool1");
            assertTrue(toolOpt.isPresent());
            assertEquals("my-server", toolOpt.get().getServerId());
            assertEquals("http://my-server:8080/sse", toolOpt.get().getServerUrl());
        }

        @Test
        @DisplayName("McpToolExecutor - 查询工具定义")
        void testGetToolDefinition() {
            registerTestServer();

            Optional<McpToolDefinition> tool = executor.getToolDefinition("audio_to_text");
            assertTrue(tool.isPresent());
            assertEquals("语音听写", tool.get().getDescription());
        }

        @Test
        @DisplayName("McpToolExecutor - 查询不存在的工具")
        void testGetNonExistentTool() {
            registerTestServer();

            Optional<McpToolDefinition> tool = executor.getToolDefinition("non_existent");
            assertTrue(tool.isEmpty());
        }

        @Test
        @DisplayName("McpToolExecutor - 检查工具是否存在")
        void testHasTool() {
            registerTestServer();

            assertTrue(executor.hasTool("audio_to_text"));
            assertFalse(executor.hasTool("non_existent"));
        }

        @Test
        @DisplayName("McpToolExecutor - 获取所有工具")
        void testGetAllTools() {
            registerTestServer();

            List<McpToolDefinition> allTools = executor.getAllTools();
            assertEquals(2, allTools.size());
        }

        @Test
        @DisplayName("McpToolExecutor - 按服务器获取工具")
        void testGetToolsByServer() {
            registerTestServer();

            List<McpToolDefinition> serverTools = executor.getToolsByServer("iat-server");
            assertEquals(2, serverTools.size());
        }

        @Test
        @DisplayName("McpToolExecutor - 注销服务器")
        void testUnregisterServer() {
            registerTestServer();
            assertEquals(2, executor.getToolCount());

            executor.unregisterServer("iat-server");
            assertEquals(0, executor.getToolCount());
            assertEquals(0, executor.getServerCount());
        }

        @Test
        @DisplayName("McpToolExecutor - 执行不存在的工具抛异常")
        void testExecuteNonExistentTool() {
            assertThrows(IOException.class, () -> executor.executeTool("non_existent", new HashMap<>()));
        }

        @Test
        @DisplayName("McpToolExecutor - 获取连接统计")
        void testGetConnectionStats() {
            registerTestServer();

            McpConnectionPool.PoolStats stats = executor.getConnectionStats("iat-server");
            assertNotNull(stats);
            assertEquals(0, stats.activeConnections());
        }

        @Test
        @DisplayName("McpToolExecutor - 注册无工具的服务器")
        void testRegisterServerWithoutTools() {
            McpServerConfig config = McpServerConfig.builder()
                    .serverId("empty-server")
                    .serverName("空服务器")
                    .serverUrl("http://localhost:8080/sse")
                    .build();

            executor.registerServer(config);

            assertEquals(0, executor.getToolCount());
            assertEquals(1, executor.getServerCount());
        }

        private void registerTestServer() {
            List<McpToolDefinition> tools = List.of(
                    McpToolDefinition.builder().name("audio_to_text").description("语音听写")
                            .serverUrl("http://localhost/sse").build(),
                    McpToolDefinition.builder().name("text_translate").description("文本翻译")
                            .serverUrl("http://localhost/sse").build()
            );

            McpServerConfig config = McpServerConfig.builder()
                    .serverId("iat-server")
                    .serverName("语音听写服务")
                    .serverUrl("http://localhost:8080/sse")
                    .tools(tools)
                    .build();

            executor.registerServer(config);
        }
    }

    @Nested
    @DisplayName("McpToolAdapter 测试")
    class McpToolAdapterTest {

        private McpToolAdapter adapter;
        private McpConnectionPool connectionPool;
        private OkHttpClientPool httpClientPool;

        @BeforeEach
        void initAdapter() {
            connectionPool = new McpConnectionPool(3, 5000, 10000, 30000, 60000);
            McpToolExecutor executor = new McpToolExecutor(connectionPool);
            httpClientPool = new OkHttpClientPool();
            adapter = new McpToolAdapter(executor, httpClientPool);
        }

        @AfterEach
        void cleanupAdapter() {
            if (connectionPool != null) {
                connectionPool.shutdown();
            }
            if (httpClientPool != null) {
                httpClientPool.shutdown();
            }
        }

        @Test
        @DisplayName("McpToolAdapter - 列出可用工具（空注册表）")
        void testListAvailableToolsEmpty() {
            List<McpToolDefinition> tools = adapter.listAvailableTools();
            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("McpToolAdapter - 获取工具 Schema（不存在）")
        void testGetToolSchemaNonExistent() {
            Map<String, Object> schema = adapter.getToolSchema("non_existent");
            assertTrue(schema.isEmpty());
        }

        @Test
        @DisplayName("McpToolAdapter - 获取 HTTP 客户端池")
        void testGetHttpClientPool() {
            OkHttpClientPool pool = adapter.getHttpClientPool();
            assertNotNull(pool);
        }

        @Test
        @DisplayName("McpToolAdapter - 获取 MCP 工具执行器")
        void testGetMcpToolExecutor() {
            McpToolExecutor executor = adapter.getMcpToolExecutor();
            assertNotNull(executor);
        }

        @Test
        @DisplayName("McpToolAdapter - 调用不存在的工具抛异常")
        void testCallNonExistentTool() {
            assertThrows(UnsupportedOperationException.class,
                    () -> adapter.callTool("non_existent", new HashMap<>()));
        }

        @Test
        @DisplayName("McpToolAdapter - 注册服务器后获取工具 Schema")
        void testGetToolSchemaAfterRegistration() {
            List<McpToolDefinition> tools = List.of(
                    McpToolDefinition.builder().name("audio_to_text").description("语音听写")
                            .inputSchema(Map.of("type", "object"))
                            .serverUrl("http://localhost/sse").build()
            );

            McpServerConfig config = McpServerConfig.builder()
                    .serverId("iat-server")
                    .serverName("语音听写服务")
                    .serverUrl("http://localhost:8080/sse")
                    .tools(tools)
                    .build();

            adapter.getMcpToolExecutor().registerServer(config);

            Map<String, Object> schema = adapter.getToolSchema("audio_to_text");
            assertFalse(schema.isEmpty());
            assertEquals("audio_to_text", schema.get("name"));
            assertEquals("语音听写", schema.get("description"));
            assertEquals("SSE", schema.get("transportType"));
            assertNotNull(schema.get("inputSchema"));
        }
    }

    @Nested
    @DisplayName("McpSseClient 测试")
    class McpSseClientTest {

        @Test
        @DisplayName("McpSseClient - 创建客户端实例")
        void testCreateClient() {
            OkHttpClient httpClient = new OkHttpClient.Builder().build();
            McpSseClient client = new McpSseClient(
                    "test-server",
                    "http://localhost:8080/sse",
                    httpClient
            );

            assertEquals("test-server", client.getServerId());
            assertEquals("http://localhost:8080/sse", client.getServerUrl());
            assertFalse(client.isConnected());
        }

        @Test
        @DisplayName("McpSseClient - 断开连接")
        void testDisconnect() {
            OkHttpClient httpClient = new OkHttpClient.Builder().build();
            McpSseClient client = new McpSseClient(
                    "test-server",
                    "http://localhost:8080/sse",
                    httpClient
            );

            assertDoesNotThrow(() -> client.disconnect());
            assertFalse(client.isConnected());
        }

        @Test
        @DisplayName("McpSseClient - 未初始化时调用工具抛异常")
        void testCallToolBeforeInit() {
            OkHttpClient httpClient = new OkHttpClient.Builder().build();
            McpSseClient client = new McpSseClient(
                    "test-server",
                    "http://localhost:8080/sse",
                    httpClient
            );

            assertThrows(IOException.class,
                    () -> client.callTool("test-tool", new HashMap<>()));
        }

        @Test
        @DisplayName("McpSseClient - 未初始化时列出工具抛异常")
        void testListToolsBeforeInit() {
            OkHttpClient httpClient = new OkHttpClient.Builder().build();
            McpSseClient client = new McpSseClient(
                    "test-server",
                    "http://localhost:8080/sse",
                    httpClient
            );

            assertThrows(IOException.class, () -> client.listTools());
        }

        @Test
        @DisplayName("McpSseClient - 最后活动时间初始化")
        void testLastActivityTime() {
            OkHttpClient httpClient = new OkHttpClient.Builder().build();
            McpSseClient client = new McpSseClient(
                    "test-server",
                    "http://localhost:8080/sse",
                    httpClient
            );

            long lastActivity = client.getLastActivityTime();
            assertTrue(lastActivity > 0, "Last activity time should be initialized");
            assertTrue(lastActivity <= System.currentTimeMillis(), "Should not be in the future");
        }

        @Test
        @DisplayName("McpSseClient - 连接到不可达服务器抛异常")
        void testConnectToUnreachableServer() {
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectTimeout(1, TimeUnit.SECONDS)
                    .readTimeout(1, TimeUnit.SECONDS)
                    .build();

            McpSseClient client = new McpSseClient(
                    "unreachable-server",
                    "http://localhost:1/sse",
                    httpClient
            );

            assertThrows(IOException.class, client::connect);
            client.disconnect();
        }

        @Test
        @DisplayName("McpSseClient - 多次断开连接不抛异常")
        void testMultipleDisconnect() {
            OkHttpClient httpClient = new OkHttpClient.Builder().build();
            McpSseClient client = new McpSseClient(
                    "test-server",
                    "http://localhost:8080/sse",
                    httpClient
            );

            assertDoesNotThrow(() -> {
                client.disconnect();
                client.disconnect();
                client.disconnect();
            });
        }
    }

    @Nested
    @DisplayName("MCP 协议 JSON-RPC 消息格式测试")
    class McpJsonRpcFormatTest {

        @Test
        @DisplayName("JSON-RPC 2.0 - initialize 请求格式")
        void testInitializeRequestFormat() {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", "1");
            request.put("method", "initialize");
            Map<String, Object> params = new LinkedHashMap<>();
            Map<String, Object> capabilities = new LinkedHashMap<>();
            capabilities.put("roots", Map.of());
            capabilities.put("sampling", Map.of());
            params.put("capabilities", capabilities);
            params.put("clientInfo", Map.of("name", "IntelligentFlow-MCP-Client", "version", "1.0.0"));
            request.put("params", params);

            assertEquals("2.0", request.get("jsonrpc"));
            assertEquals("initialize", request.get("method"));
            assertNotNull(request.get("params"));
        }

        @Test
        @DisplayName("JSON-RPC 2.0 - tools/list 请求格式")
        void testToolsListRequestFormat() {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", "2");
            request.put("method", "tools/list");
            request.put("params", Map.of());

            assertEquals("2.0", request.get("jsonrpc"));
            assertEquals("tools/list", request.get("method"));
        }

        @Test
        @DisplayName("JSON-RPC 2.0 - tools/call 请求格式")
        void testToolsCallRequestFormat() {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", "3");
            request.put("method", "tools/call");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", "audio_to_text");
            params.put("arguments", Map.of("audio_url", "http://example.com/audio.mp3"));
            request.put("params", params);

            assertEquals("2.0", request.get("jsonrpc"));
            assertEquals("tools/call", request.get("method"));
            assertEquals("audio_to_text", ((Map<?, ?>) request.get("params")).get("name"));
        }

        @Test
        @DisplayName("JSON-RPC 2.0 - tools/list 响应解析")
        void testToolsListResponseParsing() {
            String jsonResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": "2",
                  "result": {
                    "tools": [
                      {
                        "name": "audio_to_text",
                        "description": "语音听写工具",
                        "inputSchema": {
                          "type": "object",
                          "properties": {
                            "audio_url": {"type": "string", "description": "音频URL"}
                          },
                          "required": ["audio_url"]
                        }
                      },
                      {
                        "name": "text_translate",
                        "description": "文本翻译工具",
                        "inputSchema": {
                          "type": "object",
                          "properties": {
                            "text": {"type": "string"},
                            "target_lang": {"type": "string"}
                          },
                          "required": ["text", "target_lang"]
                        }
                      }
                    ]
                  }
                }
                """;

            JSONObject response = JSON.parseObject(jsonResponse);
            JSONObject result = response.getJSONObject("result");
            List<JSONObject> tools = result.getJSONArray("tools").toJavaList(JSONObject.class);

            assertEquals(2, tools.size());
            assertEquals("audio_to_text", tools.get(0).getString("name"));
            assertEquals("语音听写工具", tools.get(0).getString("description"));
            assertNotNull(tools.get(0).getJSONObject("inputSchema"));
        }

        @Test
        @DisplayName("JSON-RPC 2.0 - tools/call 响应解析")
        void testToolsCallResponseParsing() {
            String jsonResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": "3",
                  "result": {
                    "content": [
                      {
                        "type": "text",
                        "text": "这是语音识别的结果文本"
                      }
                    ],
                    "isError": false
                  }
                }
                """;

            JSONObject response = JSON.parseObject(jsonResponse);
            JSONObject result = response.getJSONObject("result");

            assertFalse(result.getBooleanValue("isError"));
            List<JSONObject> content = result.getJSONArray("content").toJavaList(JSONObject.class);
            assertEquals(1, content.size());
            assertEquals("text", content.get(0).getString("type"));
            assertEquals("这是语音识别的结果文本", content.get(0).getString("text"));
        }

        @Test
        @DisplayName("JSON-RPC 2.0 - 错误响应格式")
        void testErrorResponseFormat() {
            String jsonResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": "3",
                  "error": {
                    "code": -32600,
                    "message": "Invalid Request"
                  }
                }
                """;

            JSONObject response = JSON.parseObject(jsonResponse);
            assertTrue(response.containsKey("error"));
            JSONObject error = response.getJSONObject("error");
            assertEquals(-32600, error.getIntValue("code"));
            assertEquals("Invalid Request", error.getString("message"));
        }

        @Test
        @DisplayName("JSON-RPC 2.0 - notifications/initialized 通知格式")
        void testInitializedNotificationFormat() {
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "notifications/initialized");
            notification.put("params", Map.of());

            assertEquals("2.0", notification.get("jsonrpc"));
            assertEquals("notifications/initialized", notification.get("method"));
            assertFalse(notification.containsKey("id"), "Notifications should not have an id");
        }
    }

    @Nested
    @DisplayName("MCP 连接池并发测试")
    class McpConnectionPoolConcurrencyTest {

        @Test
        @DisplayName("McpConnectionPool - 并发注册服务器")
        void testConcurrentServerRegistration() throws InterruptedException {
            McpConnectionPool pool = new McpConnectionPool(5, 5000, 10000, 30000, 60000);
            try {
                int threadCount = 10;
                CountDownLatch latch = new CountDownLatch(threadCount);
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);

                for (int i = 0; i < threadCount; i++) {
                    final int index = i;
                    executor.submit(() -> {
                        try {
                            McpServerConfig config = McpServerConfig.builder()
                                    .serverId("server-" + index)
                                    .serverName("服务器" + index)
                                    .serverUrl("http://localhost:" + (8080 + index) + "/sse")
                                    .build();
                            pool.registerServer(config);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertTrue(latch.await(10, TimeUnit.SECONDS));
                assertEquals(threadCount, pool.getRegisteredServerCount());
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("McpConnectionPool - 并发获取连接统计")
        void testConcurrentStatsAccess() throws InterruptedException {
            McpConnectionPool pool = new McpConnectionPool(5, 5000, 10000, 30000, 60000);

            McpServerConfig config = McpServerConfig.builder()
                    .serverId("test-server")
                    .serverName("测试服务器")
                    .serverUrl("http://localhost:8080/sse")
                    .build();
            pool.registerServer(config);

            try {
                int threadCount = 20;
                CountDownLatch latch = new CountDownLatch(threadCount);
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);

                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            McpConnectionPool.PoolStats stats = pool.getStats("test-server");
                            assertNotNull(stats);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertTrue(latch.await(10, TimeUnit.SECONDS));
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("OkHttpClientPool - 并发获取客户端")
        void testConcurrentClientAccess() throws InterruptedException {
            OkHttpClientPool pool = new OkHttpClientPool(10, 60000, 5000, 10000, 10000);
            try {
                int threadCount = 20;
                CountDownLatch latch = new CountDownLatch(threadCount);
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);

                for (int i = 0; i < threadCount; i++) {
                    final int index = i;
                    executor.submit(() -> {
                        try {
                            OkHttpClient client = pool.getClient("concurrent-tag-" + (index % 5));
                            assertNotNull(client);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertTrue(latch.await(10, TimeUnit.SECONDS));
            } finally {
                pool.shutdown();
            }
        }
    }

    @Nested
    @DisplayName("MCP 与现有系统集成测试")
    class McpIntegrationTest {

        @Test
        @DisplayName("MCP 工具定义兼容 iat.json 格式")
        void testCompatibilityWithIatJson() {
            Map<String, Object> inputSchema = new LinkedHashMap<>();
            inputSchema.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("audio_url", Map.of("title", "Audio Url", "type", "string"));
            properties.put("language", Map.of("default", "zh_cn", "title", "Language", "type", "string"));
            properties.put("domain", Map.of("default", "iat", "title", "Domain", "type", "string"));
            inputSchema.put("properties", properties);
            inputSchema.put("required", List.of("audio_url"));
            inputSchema.put("title", "audio_to_textArguments");

            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("audio_to_text")
                    .description("IAT语音听写，通过传入音频文件的url，获取对应的文本内容")
                    .inputSchema(inputSchema)
                    .serverId("iat-server")
                    .serverUrl("http://xingchen-api.xf-yun.com/mcp/7361598865641885696/sse")
                    .serverName("实时语音听写")
                    .transportType(McpToolDefinition.McpTransportType.SSE)
                    .enabled(true)
                    .build();

            assertTrue(tool.hasRequiredFields());
            assertEquals("audio_to_text", tool.getName());
            assertNotNull(tool.getInputSchema());
            assertEquals("object", tool.getInputSchema().get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) tool.getInputSchema().get("properties");
            assertTrue(props.containsKey("audio_url"));
            assertTrue(props.containsKey("language"));
        }

        @Test
        @DisplayName("MCP 适配器桥接工具调用")
        void testAdapterBridgeToolCall() {
            McpConnectionPool connectionPool = new McpConnectionPool(3, 5000, 10000, 30000, 60000);
            McpToolExecutor toolExecutor = new McpToolExecutor(connectionPool);
            OkHttpClientPool httpClientPool = new OkHttpClientPool();
            McpToolAdapter adapter = new McpToolAdapter(toolExecutor, httpClientPool);

            try {
                List<McpToolDefinition> tools = List.of(
                        McpToolDefinition.builder()
                                .name("audio_to_text")
                                .description("语音听写")
                                .inputSchema(Map.of("type", "object"))
                                .serverUrl("http://localhost/sse")
                                .build()
                );

                McpServerConfig config = McpServerConfig.builder()
                        .serverId("iat-server")
                        .serverName("语音听写服务")
                        .serverUrl("http://localhost:8080/sse")
                        .tools(tools)
                        .build();

                toolExecutor.registerServer(config);

                List<McpToolDefinition> availableTools = adapter.listAvailableTools();
                assertEquals(1, availableTools.size());
                assertEquals("audio_to_text", availableTools.get(0).getName());

                Map<String, Object> schema = adapter.getToolSchema("audio_to_text");
                assertEquals("audio_to_text", schema.get("name"));
                assertEquals("SSE", schema.get("transportType"));
            } finally {
                connectionPool.shutdown();
                httpClientPool.shutdown();
            }
        }

        @Test
        @DisplayName("MCP 多服务器工具注册与查询")
        void testMultiServerToolRegistration() {
            McpConnectionPool connectionPool = new McpConnectionPool(3, 5000, 10000, 30000, 60000);
            McpToolExecutor toolExecutor = new McpToolExecutor(connectionPool);

            try {
                List<McpToolDefinition> iatTools = List.of(
                        McpToolDefinition.builder().name("audio_to_text").description("语音听写")
                                .serverUrl("http://localhost:8080/sse").build()
                );

                List<McpToolDefinition> translateTools = List.of(
                        McpToolDefinition.builder().name("text_translate").description("文本翻译")
                                .serverUrl("http://localhost:8081/sse").build()
                );

                toolExecutor.registerServer(McpServerConfig.builder()
                        .serverId("iat-server")
                        .serverName("语音听写服务")
                        .serverUrl("http://localhost:8080/sse")
                        .tools(iatTools)
                        .build());

                toolExecutor.registerServer(McpServerConfig.builder()
                        .serverId("translate-server")
                        .serverName("翻译服务")
                        .serverUrl("http://localhost:8081/sse")
                        .tools(translateTools)
                        .build());

                assertEquals(2, toolExecutor.getToolCount());
                assertEquals(2, toolExecutor.getServerCount());
                assertTrue(toolExecutor.hasTool("audio_to_text"));
                assertTrue(toolExecutor.hasTool("text_translate"));

                List<McpToolDefinition> iatServerTools = toolExecutor.getToolsByServer("iat-server");
                assertEquals(1, iatServerTools.size());
                assertEquals("audio_to_text", iatServerTools.get(0).getName());

                List<McpToolDefinition> translateServerTools = toolExecutor.getToolsByServer("translate-server");
                assertEquals(1, translateServerTools.size());
                assertEquals("text_translate", translateServerTools.get(0).getName());
            } finally {
                connectionPool.shutdown();
            }
        }

        @Test
        @DisplayName("MCP 完整流程：注册 -> 查询 -> 注销")
        void testFullLifecycle() {
            McpConnectionPool connectionPool = new McpConnectionPool(3, 5000, 10000, 30000, 60000);
            McpToolExecutor toolExecutor = new McpToolExecutor(connectionPool);
            OkHttpClientPool httpClientPool = new OkHttpClientPool();
            McpToolAdapter adapter = new McpToolAdapter(toolExecutor, httpClientPool);

            try {
                assertEquals(0, adapter.listAvailableTools().size());

                List<McpToolDefinition> tools = List.of(
                        McpToolDefinition.builder().name("audio_to_text").description("语音听写")
                                .inputSchema(Map.of("type", "object", "properties",
                                        Map.of("audio_url", Map.of("type", "string"))))
                                .serverUrl("http://localhost/sse").build(),
                        McpToolDefinition.builder().name("text_translate").description("文本翻译")
                                .inputSchema(Map.of("type", "object"))
                                .serverUrl("http://localhost/sse").build()
                );

                toolExecutor.registerServer(McpServerConfig.builder()
                        .serverId("iat-server")
                        .serverName("语音听写服务")
                        .serverUrl("http://localhost:8080/sse")
                        .tools(tools)
                        .build());

                assertEquals(2, adapter.listAvailableTools().size());
                assertTrue(adapter.getMcpToolExecutor().hasTool("audio_to_text"));

                Map<String, Object> schema = adapter.getToolSchema("audio_to_text");
                assertEquals("audio_to_text", schema.get("name"));
                assertEquals("SSE", schema.get("transportType"));
                assertEquals("iat-server", schema.get("serverId"));

                toolExecutor.unregisterServer("iat-server");
                assertEquals(0, adapter.listAvailableTools().size());
                assertTrue(adapter.getToolSchema("audio_to_text").isEmpty());
            } finally {
                connectionPool.shutdown();
                httpClientPool.shutdown();
            }
        }
    }
}
