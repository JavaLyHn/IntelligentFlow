package com.lyhn.coreworkflowjava.workflow.engine.context;

import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ContextManagerTest {

    private ContextManager contextManager;
    private InMemoryContextStorage storage;
    private static Path tempDir;

    @BeforeAll
    static void setupTempDir() throws IOException {
        tempDir = Files.createTempDirectory("context-manager-test");
    }

    @AfterAll
    static void cleanupTempDir() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
    }

    @BeforeEach
    void setUp() {
        storage = new InMemoryContextStorage(tempDir);
        contextManager = new ContextManager(storage, 200, 50);
    }

    @Nested
    @DisplayName("ContextEntry 测试")
    class ContextEntryTest {

        @Test
        @DisplayName("ContextEntry - 创建工厂方法")
        void testFactoryMethod() {
            ContextEntry entry = ContextEntry.of("session-1", "search_result",
                    "full content", "summary", "/path/to/file", 100);

            assertNotNull(entry.getEntryId());
            assertEquals("session-1", entry.getSessionId());
            assertEquals("search_result", entry.getType());
            assertEquals("summary", entry.getSummary());
            assertEquals("/path/to/file", entry.getStoragePath());
            assertEquals(100, entry.getOriginalSize());
            assertTrue(entry.isExternalized());
        }

        @Test
        @DisplayName("ContextEntry - 无存储路径时非外置")
        void testNotExternalizedWithoutPath() {
            ContextEntry entry = ContextEntry.of("session-1", "search_result",
                    "short content", "short content", null, 10);

            assertFalse(entry.isExternalized());
        }
    }

    @Nested
    @DisplayName("CompressedContext 测试")
    class CompressedContextTest {

        @Test
        @DisplayName("CompressedContext - 添加条目")
        void testAddEntry() {
            CompressedContext context = CompressedContext.builder()
                    .sessionId("session-1").build();

            ContextEntry entry1 = ContextEntry.builder()
                    .entryId("e1").sessionId("session-1").type("search")
                    .summary("summary1").originalSize(100).compressedSize(10)
                    .externalized(false).build();
            ContextEntry entry2 = ContextEntry.builder()
                    .entryId("e2").sessionId("session-1").type("tool_output")
                    .summary("summary2").originalSize(500).compressedSize(20)
                    .externalized(true).storagePath("/path/e2").build();

            context.addEntry(entry1);
            context.addEntry(entry2);

            assertEquals(2, context.getEntryCount());
            assertEquals(600, context.getTotalOriginalSize());
            assertEquals(30, context.getTotalCompressedSize());
        }

        @Test
        @DisplayName("CompressedContext - 删除条目")
        void testRemoveEntry() {
            CompressedContext context = CompressedContext.builder()
                    .sessionId("session-1").build();

            ContextEntry entry = ContextEntry.builder()
                    .entryId("e1").sessionId("session-1").type("search")
                    .summary("summary1").originalSize(100).compressedSize(10)
                    .externalized(false).build();

            context.addEntry(entry);
            assertEquals(1, context.getEntryCount());

            context.removeEntry("e1");
            assertEquals(0, context.getEntryCount());
            assertEquals(0, context.getTotalOriginalSize());
        }

        @Test
        @DisplayName("CompressedContext - 查找条目")
        void testFindEntry() {
            CompressedContext context = CompressedContext.builder()
                    .sessionId("session-1").build();

            ContextEntry entry = ContextEntry.builder()
                    .entryId("e1").sessionId("session-1").type("search")
                    .summary("summary1").originalSize(100).compressedSize(10)
                    .build();

            context.addEntry(entry);

            assertNotNull(context.findEntry("e1"));
            assertNull(context.findEntry("non-existent"));
        }

        @Test
        @DisplayName("CompressedContext - 按类型查找")
        void testFindByType() {
            CompressedContext context = CompressedContext.builder()
                    .sessionId("session-1").build();

            context.addEntry(ContextEntry.builder()
                    .entryId("e1").sessionId("session-1").type("search")
                    .summary("s1").originalSize(100).compressedSize(10).build());
            context.addEntry(ContextEntry.builder()
                    .entryId("e2").sessionId("session-1").type("tool_output")
                    .summary("s2").originalSize(200).compressedSize(20).build());
            context.addEntry(ContextEntry.builder()
                    .entryId("e3").sessionId("session-1").type("search")
                    .summary("s3").originalSize(300).compressedSize(30).build());

            assertEquals(2, context.findEntriesByType("search").size());
            assertEquals(1, context.findEntriesByType("tool_output").size());
        }

        @Test
        @DisplayName("CompressedContext - 压缩率计算")
        void testCompressionRatio() {
            CompressedContext context = CompressedContext.builder()
                    .sessionId("session-1").build();

            context.addEntry(ContextEntry.builder()
                    .entryId("e1").sessionId("session-1").type("search")
                    .summary("s1").originalSize(1000).compressedSize(100)
                    .build());

            assertEquals(0.1, context.getCompressionRatio(), 0.001);
        }

        @Test
        @DisplayName("CompressedContext - 构建上下文摘要")
        void testBuildContextSummary() {
            CompressedContext context = CompressedContext.builder()
                    .sessionId("session-1").build();

            context.addEntry(ContextEntry.builder()
                    .entryId("e1").sessionId("session-1").type("search")
                    .summary("AI research findings").originalSize(1000).compressedSize(20)
                    .externalized(true).storagePath("/ctx/e1.json").build());
            context.addEntry(ContextEntry.builder()
                    .entryId("e2").sessionId("session-1").type("tool_output")
                    .summary("Short result").originalSize(50).compressedSize(12)
                    .externalized(false).build());

            String summary = context.buildContextSummary();

            assertTrue(summary.contains("[search]"));
            assertTrue(summary.contains("AI research findings"));
            assertTrue(summary.contains("ref: /ctx/e1.json"));
            assertTrue(summary.contains("[tool_output]"));
            assertTrue(summary.contains("Short result"));
        }
    }

    @Nested
    @DisplayName("ContextFragment 测试")
    class ContextFragmentTest {

        @Test
        @DisplayName("ContextFragment - 创建工厂方法")
        void testFactoryMethod() {
            ContextFragment fragment = ContextFragment.of("e1", "session-1", 10, 50, "content");

            assertEquals("e1", fragment.getEntryId());
            assertEquals("session-1", fragment.getSessionId());
            assertEquals(10, fragment.getStartOffset());
            assertEquals(50, fragment.getEndOffset());
            assertEquals("content", fragment.getContent());
        }
    }

    @Nested
    @DisplayName("ContextManager 核心功能测试")
    class ContextManagerCoreTest {

        @Test
        @DisplayName("添加短上下文 - 不触发外置")
        void testAddShortContext() {
            ContextEntry entry = contextManager.addContext("session-1", "search_result", "Short content");

            assertNotNull(entry);
            assertEquals("session-1", entry.getSessionId());
            assertEquals("search_result", entry.getType());
            assertFalse(entry.isExternalized());
            assertEquals("Short content", entry.getSummary());
        }

        @Test
        @DisplayName("添加长上下文 - 触发外置压缩")
        void testAddLongContext() {
            String longContent = "A".repeat(500);

            ContextEntry entry = contextManager.addContext("session-1", "search_result", longContent);

            assertNotNull(entry);
            assertTrue(entry.isExternalized());
            assertEquals(500, entry.getOriginalSize());
            assertTrue(entry.getCompressedSize() < entry.getOriginalSize());
            assertNotNull(entry.getStoragePath());
            assertTrue(entry.getSummary().length() <= 50 + 20);
        }

        @Test
        @DisplayName("回读外置上下文 - 可逆压缩")
        void testRetrieveExternalizedContent() {
            String longContent = "This is a long search result with detailed information. " +
                    "It contains multiple paragraphs of text that exceed the compression threshold. " +
                    "The content should be fully recoverable after compression and externalization.";

            ContextEntry entry = contextManager.addContext("session-1", "search_result", longContent);
            assertTrue(entry.isExternalized());

            String retrieved = contextManager.retrieveFullContent("session-1", entry.getEntryId());
            assertNotNull(retrieved);
            assertEquals(longContent, retrieved);
        }

        @Test
        @DisplayName("回读非外置上下文")
        void testRetrieveNonExternalizedContent() {
            ContextEntry entry = contextManager.addContext("session-1", "search_result", "Short content");
            assertFalse(entry.isExternalized());

            String retrieved = contextManager.retrieveFullContent("session-1", entry.getEntryId());
            assertEquals("Short content", retrieved);
        }

        @Test
        @DisplayName("片段级检索 - 指定偏移范围")
        void testRetrieveFragment() {
            String longContent = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            ContextEntry entry = contextManager.addContext("session-1", "search_result", longContent);

            ContextFragment fragment = contextManager.retrieveFragment(
                    "session-1", entry.getEntryId(), 10, 20);

            assertNotNull(fragment);
            assertEquals(10, fragment.getStartOffset());
            assertEquals(20, fragment.getEndOffset());
            assertEquals("ABCDEFGHIJ", fragment.getContent());
        }

        @Test
        @DisplayName("片段级检索 - 超出范围安全处理")
        void testRetrieveFragmentOutOfRange() {
            ContextEntry entry = contextManager.addContext("session-1", "search_result", "Short");

            ContextFragment fragment = contextManager.retrieveFragment(
                    "session-1", entry.getEntryId(), 0, 100);

            assertNotNull(fragment);
            assertEquals("Short", fragment.getContent());
        }

        @Test
        @DisplayName("关键词搜索 - 片段级检索")
        void testSearchFragments() {
            String content1 = "The quick brown fox jumps over the lazy dog. " +
                    "Artificial intelligence is transforming industries worldwide.";
            String content2 = "Machine learning models require large datasets. " +
                    "The fox is a clever animal in many stories.";

            contextManager.addContext("session-1", "search_result", content1);
            contextManager.addContext("session-1", "tool_output", content2);

            List<ContextFragment> fragments = contextManager.searchFragments("session-1", "fox", 10);

            assertFalse(fragments.isEmpty());
            for (ContextFragment f : fragments) {
                assertTrue(f.getContent().contains("fox"));
            }
        }

        @Test
        @DisplayName("获取压缩摘要 - 仅保留路径+摘要")
        void testGetCompressedSummary() {
            String longContent = "A".repeat(500);
            contextManager.addContext("session-1", "search_result", longContent);
            contextManager.addContext("session-1", "tool_output", "Short result");

            String summary = contextManager.getCompressedSummary("session-1");

            assertTrue(summary.contains("[search_result]"));
            assertTrue(summary.contains("[tool_output]"));
            assertTrue(summary.contains("Short result"));
        }

        @Test
        @DisplayName("删除上下文条目")
        void testRemoveEntry() {
            ContextEntry entry = contextManager.addContext("session-1", "search_result", "Content to remove");

            boolean removed = contextManager.removeEntry("session-1", entry.getEntryId());
            assertTrue(removed);

            String retrieved = contextManager.retrieveFullContent("session-1", entry.getEntryId());
            assertNull(retrieved);
        }

        @Test
        @DisplayName("压缩率统计")
        void testCompressionRatio() {
            String longContent = "A".repeat(1000);
            contextManager.addContext("session-1", "search_result", longContent);

            double ratio = contextManager.getCompressionRatio("session-1");
            assertTrue(ratio < 1.0, "Compressed size should be smaller than original");
            assertTrue(ratio > 0.0, "Compression ratio should be positive");
        }

        @Test
        @DisplayName("原始大小和压缩大小统计")
        void testSizeStatistics() {
            String longContent = "A".repeat(1000);
            contextManager.addContext("session-1", "search_result", longContent);
            contextManager.addContext("session-1", "tool_output", "Short");

            assertEquals(1000 + 5, contextManager.getTotalOriginalSize("session-1"));
            assertTrue(contextManager.getTotalCompressedSize("session-1") < 1000 + 5);
        }
    }

    @Nested
    @DisplayName("可逆上下文压缩机制测试")
    class ReversibleCompressionTest {

        @Test
        @DisplayName("可逆性 - 外置后完整回读与原文一致")
        void testReversibility() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("Line ").append(i).append(": This is a detailed search result with information about topic ").append(i).append(".\n");
            }
            String original = sb.toString();

            ContextEntry entry = contextManager.addContext("session-1", "search_result", original);
            assertTrue(entry.isExternalized());

            String retrieved = contextManager.retrieveFullContent("session-1", entry.getEntryId());
            assertEquals(original, retrieved, "Retrieved content should match original");
        }

        @Test
        @DisplayName("可逆性 - 多个外置条目独立回读")
        void testMultipleEntriesReversibility() {
            String content1 = "Content A: " + "X".repeat(300);
            String content2 = "Content B: " + "Y".repeat(400);
            String content3 = "Content C: " + "Z".repeat(500);

            ContextEntry e1 = contextManager.addContext("session-1", "search", content1);
            ContextEntry e2 = contextManager.addContext("session-1", "search", content2);
            ContextEntry e3 = contextManager.addContext("session-1", "tool_output", content3);

            assertTrue(e1.isExternalized());
            assertTrue(e2.isExternalized());
            assertTrue(e3.isExternalized());

            assertEquals(content1, contextManager.retrieveFullContent("session-1", e1.getEntryId()));
            assertEquals(content2, contextManager.retrieveFullContent("session-1", e2.getEntryId()));
            assertEquals(content3, contextManager.retrieveFullContent("session-1", e3.getEntryId()));
        }

        @Test
        @DisplayName("上下文规模控制 - 压缩后对话上下文显著减小")
        void testContextSizeControl() {
            for (int i = 0; i < 5; i++) {
                contextManager.addContext("session-1", "search_result",
                        "Detailed result " + i + ": " + "D".repeat(500));
            }

            long originalSize = contextManager.getTotalOriginalSize("session-1");
            long compressedSize = contextManager.getTotalCompressedSize("session-1");

            assertTrue(compressedSize < originalSize,
                    "Compressed size (" + compressedSize + ") should be less than original (" + originalSize + ")");

            String summary = contextManager.getCompressedSummary("session-1");
            assertTrue(summary.length() < originalSize,
                    "Summary length should be less than original total size");
        }
    }

    @Nested
    @DisplayName("按需回读与片段级检索测试")
    class OnDemandReadbackTest {

        @Test
        @DisplayName("按需回读 - 仅在需要时读取完整内容")
        void testOnDemandReadback() {
            String longContent = "Start of content. " + "M".repeat(300) + " End of content.";
            ContextEntry entry = contextManager.addContext("session-1", "search", longContent);

            String summary = contextManager.getCompressedSummary("session-1");
            assertTrue(summary.length() < longContent.length());

            String fullContent = contextManager.retrieveFullContent("session-1", entry.getEntryId());
            assertEquals(longContent, fullContent);
        }

        @Test
        @DisplayName("片段级检索 - 精确提取指定范围")
        void testFragmentLevelRetrieval() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("Word").append(i).append(" ");
            }
            String content = sb.toString();

            ContextEntry entry = contextManager.addContext("session-1", "search", content);

            ContextFragment fragment = contextManager.retrieveFragment(
                    "session-1", entry.getEntryId(), 20, 40);

            assertNotNull(fragment);
            assertTrue(fragment.getContent().length() <= 20);
        }

        @Test
        @DisplayName("关键词搜索 - 多个匹配结果")
        void testKeywordSearchMultipleMatches() {
            String content = "Python is great. Java is also great. Both Python and Java are popular.";
            contextManager.addContext("session-1", "search", content);

            List<ContextFragment> fragments = contextManager.searchFragments("session-1", "Python", 5);

            assertEquals(2, fragments.size());
            for (ContextFragment f : fragments) {
                assertTrue(f.getContent().contains("Python"));
            }
        }

        @Test
        @DisplayName("关键词搜索 - 无匹配返回空列表")
        void testKeywordSearchNoMatch() {
            contextManager.addContext("session-1", "search", "Hello world");

            List<ContextFragment> fragments = contextManager.searchFragments("session-1", "xyz_not_found", 5);

            assertTrue(fragments.isEmpty());
        }
    }

    @Nested
    @DisplayName("多会话管理测试")
    class MultiSessionTest {

        @Test
        @DisplayName("不同会话上下文隔离")
        void testSessionIsolation() {
            ContextEntry e1 = contextManager.addContext("session-A", "search", "Content for A");
            ContextEntry e2 = contextManager.addContext("session-B", "search", "Content for B");

            String summaryA = contextManager.getCompressedSummary("session-A");
            String summaryB = contextManager.getCompressedSummary("session-B");

            assertTrue(summaryA.contains("Content for A"));
            assertTrue(summaryB.contains("Content for B"));
            assertFalse(summaryA.contains("Content for B"));
            assertFalse(summaryB.contains("Content for A"));
        }

        @Test
        @DisplayName("不同会话独立回读")
        void testSessionIndependentReadback() {
            String contentA = "A".repeat(300);
            String contentB = "B".repeat(300);

            ContextEntry eA = contextManager.addContext("session-A", "search", contentA);
            ContextEntry eB = contextManager.addContext("session-B", "search", contentB);

            assertEquals(contentA, contextManager.retrieveFullContent("session-A", eA.getEntryId()));
            assertEquals(contentB, contextManager.retrieveFullContent("session-B", eB.getEntryId()));
        }
    }

    @Nested
    @DisplayName("ContextManagerAutoConfiguration 测试")
    class AutoConfigurationTest {

        @Test
        @DisplayName("S3ClientAdapter - null S3ClientUtil 不抛异常")
        void testS3ClientAdapterWithNull() {
            S3ClientAdapter adapter = new S3ClientAdapter(null);
            assertNotNull(adapter);
        }
    }

    private static class InMemoryContextStorage extends ContextStorageService {

        private final Map<String, String> redisStore = new ConcurrentHashMap<>();
        private final Map<String, String> minioStore = new ConcurrentHashMap<>();
        private final Path tempDir;

        public InMemoryContextStorage(Path tempDir) {
            super(null, null, 7200);
            this.tempDir = tempDir;
        }

        @Override
        public void saveCompressedContext(CompressedContext context) {
            String key = "ctx:compressed:" + context.getSessionId();
            redisStore.put(key, com.alibaba.fastjson2.JSON.toJSONString(context));
        }

        @Override
        public CompressedContext loadCompressedContext(String sessionId) {
            String key = "ctx:compressed:" + sessionId;
            String json = redisStore.get(key);
            if (json == null) return null;
            return com.alibaba.fastjson2.JSON.parseObject(json, CompressedContext.class);
        }

        @Override
        public void saveEntrySummary(ContextEntry entry) {
            String key = "ctx:entry:" + entry.getSessionId() + ":" + entry.getEntryId();
            redisStore.put(key, com.alibaba.fastjson2.JSON.toJSONString(entry));
        }

        @Override
        public ContextEntry loadEntrySummary(String sessionId, String entryId) {
            String key = "ctx:entry:" + sessionId + ":" + entryId;
            String json = redisStore.get(key);
            if (json == null) return null;
            return com.alibaba.fastjson2.JSON.parseObject(json, ContextEntry.class);
        }

        @Override
        public String externalizeToMinIO(String sessionId, String entryId, String content) {
            String objectKey = "context/" + sessionId + "/" + entryId + ".json";
            minioStore.put(objectKey, content);
            return objectKey;
        }

        @Override
        public String readFromMinIO(String storagePath) {
            return minioStore.get(storagePath);
        }

        @Override
        public ContextFragment readFragmentFromMinIO(String storagePath, String sessionId,
                                                      String entryId, int startOffset, int endOffset) {
            String fullContent = minioStore.get(storagePath);
            if (fullContent == null) return null;

            int safeStart = Math.max(0, startOffset);
            int safeEnd = Math.min(fullContent.length(), endOffset);
            if (safeStart >= safeEnd) {
                return ContextFragment.of(entryId, sessionId, safeStart, safeStart, "");
            }
            return ContextFragment.of(entryId, sessionId, safeStart, safeEnd,
                    fullContent.substring(safeStart, safeEnd));
        }

        @Override
        public boolean deleteFromMinIO(String storagePath) {
            return minioStore.remove(storagePath) != null;
        }

        @Override
        public void deleteCompressedContext(String sessionId) {
            redisStore.remove("ctx:compressed:" + sessionId);
        }

        @Override
        public boolean contextExists(String sessionId) {
            return redisStore.containsKey("ctx:compressed:" + sessionId);
        }
    }
}
