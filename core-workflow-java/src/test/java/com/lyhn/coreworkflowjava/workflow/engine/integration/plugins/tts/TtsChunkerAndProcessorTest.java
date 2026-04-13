package com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.tts;

import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TtsChunkerAndProcessorTest {

    @Nested
    @DisplayName("SmartTextChunker 智能分块算法测试")
    class SmartTextChunkerTest {

        private SmartTextChunker chunker;

        @BeforeEach
        void setUp() {
            chunker = new SmartTextChunker(500, 100);
        }

        @Test
        @DisplayName("短文本不分割 - 返回单个 SINGLE 块")
        void testShortTextNoSplit() {
            String text = "这是一段短文本";
            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            assertEquals(1, chunks.size());
            assertEquals(SmartTextChunker.ChunkType.SINGLE, chunks.get(0).type());
            assertEquals(text, chunks.get(0).text());
        }

        @Test
        @DisplayName("空文本返回空列表")
        void testEmptyText() {
            List<SmartTextChunker.TextChunk> chunks = chunker.chunk("");
            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("null 文本返回空列表")
        void testNullText() {
            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(null);
            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("按句号分割 - 中文句号")
        void testSplitByChinesePeriod() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("这是第").append(i + 1).append("句话，内容比较长需要占用一些字节。");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            assertFalse(chunks.isEmpty());
            assertTrue(chunks.size() > 1, "Long text should be split into multiple chunks");

            for (SmartTextChunker.TextChunk chunk : chunks) {
                assertTrue(chunk.byteLength() <= 500,
                        "Each chunk should not exceed max bytes: " + chunk.byteLength());
            }

            boolean hasSentenceBreak = chunks.stream()
                    .anyMatch(c -> c.type() == SmartTextChunker.ChunkType.SENTENCE_BREAK);
            assertTrue(hasSentenceBreak, "Should have at least one SENTENCE_BREAK chunk");
        }

        @Test
        @DisplayName("按感叹号分割")
        void testSplitByExclamation() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 30; i++) {
                sb.append("太棒了！这是非常精彩的内容！");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            assertTrue(chunks.size() > 1);
            boolean hasSentenceBreak = chunks.stream()
                    .anyMatch(c -> c.type() == SmartTextChunker.ChunkType.SENTENCE_BREAK);
            assertTrue(hasSentenceBreak);
        }

        @Test
        @DisplayName("按问号分割")
        void testSplitByQuestionMark() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 30; i++) {
                sb.append("你今天过得怎么样？有什么新鲜事吗？");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            assertTrue(chunks.size() > 1);
        }

        @Test
        @DisplayName("按逗号分割 - 无句号时降级")
        void testSplitByComma() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 40; i++) {
                sb.append("这是一段很长的文字，没有句号结尾，只有逗号分隔，");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            assertTrue(chunks.size() > 1, "Should split long text even without sentence breaks");
            for (SmartTextChunker.TextChunk chunk : chunks) {
                assertTrue(chunk.byteLength() <= 500);
            }
        }

        @Test
        @DisplayName("按换行符分割 - 段落边界")
        void testSplitByParagraph() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append("这是第").append(i + 1).append("段的内容，包含一些描述性文字和详细信息。\n\n");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            assertTrue(chunks.size() > 1);
        }

        @Test
        @DisplayName("混合标点文本分割")
        void testMixedPunctuation() {
            StringBuilder sb = new StringBuilder();
            sb.append("大家好！欢迎来到今天的节目。");
            sb.append("我们今天要讨论的话题非常重要，希望大家认真听。");
            sb.append("首先，让我们来看第一个问题：什么是人工智能？");
            sb.append("人工智能是计算机科学的一个分支，它试图理解智能的实质。");
            sb.append("并生产出一种新的能以人类智能相似的方式做出反应的智能机器！");

            for (int i = 0; i < 5; i++) {
                sb.append("这是额外的内容填充，用来让文本超过500字节的限制。");
                sb.append("我们需要确保分块算法能正确处理各种标点符号。");
            }

            String text = sb.toString();
            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            assertTrue(chunks.size() > 1);

            String reconstructed = String.join("", chunks.stream().map(SmartTextChunker.TextChunk::text).toList());
            assertNotNull(reconstructed);
        }

        @Test
        @DisplayName("UTF-8 多字节字符安全 - 不截断中文字符")
        void testUtf8Safety() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                sb.append("中文内容");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            for (SmartTextChunker.TextChunk chunk : chunks) {
                String chunkText = chunk.text();
                byte[] chunkBytes = chunkText.getBytes(StandardCharsets.UTF_8);
                String decoded = new String(chunkBytes, StandardCharsets.UTF_8);
                assertEquals(chunkText, decoded, "Chunk text should be valid UTF-8");
            }
        }

        @Test
        @DisplayName("每个分块不超过最大字节限制")
        void testMaxBytesConstraint() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("这是一段测试文本，用于验证分块大小限制。");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            for (SmartTextChunker.TextChunk chunk : chunks) {
                assertTrue(chunk.byteLength() <= 500,
                        "Chunk byte length " + chunk.byteLength() + " exceeds max 500");
            }
        }

        @Test
        @DisplayName("英文文本分块")
        void testEnglishTextChunking() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("This is sentence number ").append(i + 1).append(". It contains some English text for testing. ");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            assertTrue(chunks.size() > 1);
            for (SmartTextChunker.TextChunk chunk : chunks) {
                assertTrue(chunk.byteLength() <= 500);
            }
        }

        @Test
        @DisplayName("自定义最大字节数")
        void testCustomMaxBytes() {
            SmartTextChunker customChunker = new SmartTextChunker(200, 50);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("这是第").append(i + 1).append("句话。");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = customChunker.chunk(text);

            for (SmartTextChunker.TextChunk chunk : chunks) {
                assertTrue(chunk.byteLength() <= 200,
                        "Chunk should respect custom max bytes: " + chunk.byteLength());
            }
        }

        @Test
        @DisplayName("小分块合并 - 避免过小的分片")
        void testSmallChunkMerging() {
            SmartTextChunker customChunker = new SmartTextChunker(300, 50);

            String text = "短。短。短。短。短。短。短。短。短。短。" +
                    "这是一段比较长的内容，用来确保合并后的分块不会太小。" +
                    "继续添加更多内容来填充字节数。" +
                    "再添加一些文字确保超过300字节限制。" +
                    "最后一段内容也在这里。";

            List<SmartTextChunker.TextChunk> chunks = customChunker.chunk(text);

            for (SmartTextChunker.TextChunk chunk : chunks) {
                assertTrue(chunk.byteLength() <= 300);
            }
        }

        @Test
        @DisplayName("分块类型统计 - 验证语义优先级")
        void testChunkTypeStatistics() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 30; i++) {
                sb.append("这是第").append(i + 1).append("句话，内容丰富。");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            Map<SmartTextChunker.ChunkType, Long> typeCounts = new EnumMap<>(SmartTextChunker.ChunkType.class);
            for (SmartTextChunker.TextChunk chunk : chunks) {
                typeCounts.merge(chunk.type(), 1L, Long::sum);
            }

            assertTrue(typeCounts.containsKey(SmartTextChunker.ChunkType.SENTENCE_BREAK) ||
                            typeCounts.containsKey(SmartTextChunker.ChunkType.CLAUSE_BREAK) ||
                            typeCounts.containsKey(SmartTextChunker.ChunkType.LAST),
                    "Should have meaningful chunk types");
        }

        @Test
        @DisplayName("TextChunk record 字段验证")
        void testTextChunkRecordFields() {
            SmartTextChunker.TextChunk chunk = new SmartTextChunker.TextChunk(
                    "测试文本", 0, 12, SmartTextChunker.ChunkType.SINGLE);

            assertEquals("测试文本", chunk.text());
            assertEquals(0, chunk.charOffset());
            assertEquals(12, chunk.byteLength());
            assertEquals(SmartTextChunker.ChunkType.SINGLE, chunk.type());
        }

        @Test
        @DisplayName("ChunkType 枚举完整性")
        void testChunkTypeEnum() {
            SmartTextChunker.ChunkType[] types = SmartTextChunker.ChunkType.values();
            assertEquals(8, types.length);

            assertNotNull(SmartTextChunker.ChunkType.valueOf("SINGLE"));
            assertNotNull(SmartTextChunker.ChunkType.valueOf("SENTENCE_BREAK"));
            assertNotNull(SmartTextChunker.ChunkType.valueOf("PARAGRAPH_BREAK"));
            assertNotNull(SmartTextChunker.ChunkType.valueOf("CLAUSE_BREAK"));
            assertNotNull(SmartTextChunker.ChunkType.valueOf("WHITESPACE_BREAK"));
            assertNotNull(SmartTextChunker.ChunkType.valueOf("FORCED"));
            assertNotNull(SmartTextChunker.ChunkType.valueOf("LAST"));
            assertNotNull(SmartTextChunker.ChunkType.valueOf("MERGED"));
        }
    }

    @Nested
    @DisplayName("ConcurrentTtsProcessor 多线程并发测试")
    class ConcurrentTtsProcessorTest {

        @Test
        @DisplayName("单块直接处理 - 不启动线程池")
        void testSingleChunkDirectProcessing() throws Exception {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor();
            List<SmartTextChunker.TextChunk> chunks = List.of(
                    new SmartTextChunker.TextChunk("测试", 0, 6, SmartTextChunker.ChunkType.SINGLE)
            );

            AtomicInteger callCount = new AtomicInteger(0);
            List<byte[]> results = processor.processChunks(chunks, (text, vcn) -> {
                callCount.incrementAndGet();
                return new byte[]{1, 2, 3};
            }, "test-vcn");

            assertEquals(1, results.size());
            assertArrayEquals(new byte[]{1, 2, 3}, results.get(0));
            assertEquals(1, callCount.get());
        }

        @Test
        @DisplayName("空块列表返回空结果")
        void testEmptyChunks() throws Exception {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor();
            List<byte[]> results = processor.processChunks(new ArrayList<>(), (text, vcn) -> new byte[0], "vcn");
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("多块并发处理 - 验证顺序保持")
        void testMultiChunkConcurrentProcessing() throws Exception {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor(4, 50, 30);

            List<SmartTextChunker.TextChunk> chunks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                chunks.add(new SmartTextChunker.TextChunk("文本" + i, 0, 10, SmartTextChunker.ChunkType.SENTENCE_BREAK));
            }

            AtomicInteger callCount = new AtomicInteger(0);
            List<byte[]> results = processor.processChunks(chunks, (text, vcn) -> {
                callCount.incrementAndGet();
                return text.getBytes(StandardCharsets.UTF_8);
            }, "vcn");

            assertEquals(6, results.size());
            assertEquals(6, callCount.get());

            for (int i = 0; i < results.size(); i++) {
                String decoded = new String(results.get(i), StandardCharsets.UTF_8);
                assertEquals("文本" + i, decoded, "Order should be preserved for chunk " + i);
            }
        }

        @Test
        @DisplayName("并发处理 - 合成函数被并发调用")
        void testConcurrentSynthesis() throws Exception {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor(4, 0, 30);

            List<SmartTextChunker.TextChunk> chunks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                chunks.add(new SmartTextChunker.TextChunk("块" + i, 0, 10, SmartTextChunker.ChunkType.SENTENCE_BREAK));
            }

            AtomicInteger maxConcurrent = new AtomicInteger(0);
            AtomicInteger currentConcurrent = new AtomicInteger(0);

            List<byte[]> results = processor.processChunks(chunks, (text, vcn) -> {
                int current = currentConcurrent.incrementAndGet();
                maxConcurrent.updateAndGet(m -> Math.max(m, current));

                Thread.sleep(100);

                currentConcurrent.decrementAndGet();
                return text.getBytes(StandardCharsets.UTF_8);
            }, "vcn");

            assertEquals(8, results.size());
            assertTrue(maxConcurrent.get() > 1,
                    "Should have concurrent calls, max was: " + maxConcurrent.get());
        }

        @Test
        @DisplayName("合成失败抛异常")
        void testSynthesisFailure() {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor(2, 0, 10);

            List<SmartTextChunker.TextChunk> chunks = List.of(
                    new SmartTextChunker.TextChunk("文本1", 0, 10, SmartTextChunker.ChunkType.SENTENCE_BREAK),
                    new SmartTextChunker.TextChunk("文本2", 0, 10, SmartTextChunker.ChunkType.SENTENCE_BREAK)
            );

            assertThrows(RuntimeException.class, () ->
                    processor.processChunks(chunks, (text, vcn) -> {
                        throw new RuntimeException("Synthesis failed");
                    }, "vcn"));
        }

        @Test
        @DisplayName("超时处理")
        void testTimeout() {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor(2, 0, 1);

            List<SmartTextChunker.TextChunk> chunks = List.of(
                    new SmartTextChunker.TextChunk("文本1", 0, 10, SmartTextChunker.ChunkType.SENTENCE_BREAK),
                    new SmartTextChunker.TextChunk("文本2", 0, 10, SmartTextChunker.ChunkType.SENTENCE_BREAK)
            );

            assertThrows(RuntimeException.class, () ->
                    processor.processChunks(chunks, (text, vcn) -> {
                        Thread.sleep(5000);
                        return new byte[0];
                    }, "vcn"));
        }

        @Test
        @DisplayName("ProcessingStats 记录创建")
        void testProcessingStats() {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor();
            ConcurrentTtsProcessor.ProcessingStats stats = processor.getProcessingStats(
                    System.currentTimeMillis() - 1000, 5, 5, 0);

            assertEquals(5, stats.totalChunks());
            assertEquals(5, stats.completedChunks());
            assertEquals(0, stats.failedChunks());
            assertTrue(stats.elapsedMs() >= 1000);
        }

        @Test
        @DisplayName("默认构造器")
        void testDefaultConstructor() {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor();
            assertNotNull(processor);
        }

        @Test
        @DisplayName("自定义并发度")
        void testCustomConcurrency() {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor(8, 100, 60);
            assertNotNull(processor);
        }

        @Test
        @DisplayName("并发度为1时串行处理")
        void testSingleConcurrency() throws Exception {
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor(1, 0, 30);

            List<SmartTextChunker.TextChunk> chunks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                chunks.add(new SmartTextChunker.TextChunk("块" + i, 0, 10, SmartTextChunker.ChunkType.SENTENCE_BREAK));
            }

            List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
            List<byte[]> results = processor.processChunks(chunks, (text, vcn) -> {
                executionOrder.add(text);
                return text.getBytes(StandardCharsets.UTF_8);
            }, "vcn");

            assertEquals(3, results.size());
        }
    }

    @Nested
    @DisplayName("WavAudioMerger 音频合并测试")
    class WavAudioMergerTest {

        private WavAudioMerger merger;

        @BeforeEach
        void setUp() {
            merger = new WavAudioMerger();
        }

        @Test
        @DisplayName("单个音频块直接返回")
        void testSingleChunkReturn() {
            byte[] singleChunk = createWavFile(100);
            List<byte[]> chunks = List.of(singleChunk);

            byte[] result = merger.merge(chunks);
            assertArrayEquals(singleChunk, result);
        }

        @Test
        @DisplayName("合并两个 WAV 文件")
        void testMergeTwoWavFiles() {
            byte[] chunk1 = createWavFile(100);
            byte[] chunk2 = createWavFile(200);

            List<byte[]> chunks = List.of(chunk1, chunk2);
            byte[] merged = merger.merge(chunks);

            int expectedDataSize = 100 + 200;
            int expectedTotalSize = 44 + expectedDataSize;
            assertEquals(expectedTotalSize, merged.length);

            int fileSizeInHeader = readLittleEndianInt(merged, 4);
            assertEquals(expectedTotalSize - 8, fileSizeInHeader);

            int dataSizeInHeader = readLittleEndianInt(merged, 40);
            assertEquals(expectedDataSize, dataSizeInHeader);
        }

        @Test
        @DisplayName("合并多个 WAV 文件")
        void testMergeMultipleWavFiles() {
            List<byte[]> chunks = new ArrayList<>();
            int totalDataSize = 0;
            for (int i = 0; i < 5; i++) {
                int dataSize = 50 * (i + 1);
                chunks.add(createWavFile(dataSize));
                totalDataSize += dataSize;
            }

            byte[] merged = merger.merge(chunks);

            int expectedTotalSize = 44 + totalDataSize;
            assertEquals(expectedTotalSize, merged.length);

            int dataSizeInHeader = readLittleEndianInt(merged, 40);
            assertEquals(totalDataSize, dataSizeInHeader);
        }

        @Test
        @DisplayName("空列表抛异常")
        void testEmptyListThrows() {
            assertThrows(IllegalArgumentException.class, () -> merger.merge(new ArrayList<>()));
        }

        @Test
        @DisplayName("null 列表抛异常")
        void testNullListThrows() {
            assertThrows(IllegalArgumentException.class, () -> merger.merge(null));
        }

        @Test
        @DisplayName("合并后 WAV 头部 RIFF 标记正确")
        void testMergedRiffHeader() {
            List<byte[]> chunks = List.of(createWavFile(100), createWavFile(100));
            byte[] merged = merger.merge(chunks);

            assertEquals('R', merged[0]);
            assertEquals('I', merged[1]);
            assertEquals('F', merged[2]);
            assertEquals('F', merged[3]);
        }

        private byte[] createWavFile(int dataSize) {
            int totalSize = 44 + dataSize;
            byte[] wav = new byte[totalSize];

            wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
            writeLittleEndianInt(wav, 4, totalSize - 8);
            wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
            wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
            writeLittleEndianInt(wav, 16, 16);
            writeLittleEndianShort(wav, 20, (short) 1);
            writeLittleEndianShort(wav, 22, (short) 1);
            writeLittleEndianInt(wav, 24, 24000);
            writeLittleEndianInt(wav, 28, 48000);
            writeLittleEndianShort(wav, 32, (short) 2);
            writeLittleEndianShort(wav, 34, (short) 16);
            wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
            writeLittleEndianInt(wav, 40, dataSize);

            for (int i = 44; i < totalSize; i++) {
                wav[i] = (byte) (i % 256);
            }

            return wav;
        }

        private void writeLittleEndianInt(byte[] data, int offset, int value) {
            data[offset] = (byte) (value & 0xFF);
            data[offset + 1] = (byte) ((value >> 8) & 0xFF);
            data[offset + 2] = (byte) ((value >> 16) & 0xFF);
            data[offset + 3] = (byte) ((value >> 24) & 0xFF);
        }

        private void writeLittleEndianShort(byte[] data, int offset, short value) {
            data[offset] = (byte) (value & 0xFF);
            data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        }

        private int readLittleEndianInt(byte[] data, int offset) {
            return (data[offset] & 0xFF) |
                    ((data[offset + 1] & 0xFF) << 8) |
                    ((data[offset + 2] & 0xFF) << 16) |
                    ((data[offset + 3] & 0xFF) << 24);
        }
    }

    @Nested
    @DisplayName("智能分块 + 并发处理 集成测试")
    class IntegrationTest {

        @Test
        @DisplayName("完整流程：智能分块 -> 并发合成 -> 音频合并")
        void testFullPipeline() throws Exception {
            SmartTextChunker chunker = new SmartTextChunker(200, 50);
            ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor(4, 50, 30);
            WavAudioMerger merger = new WavAudioMerger();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("这是第").append(i + 1).append("段测试文本，用于验证完整的TTS处理流程。");
            }
            String text = sb.toString();

            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);
            assertTrue(chunks.size() > 1, "Should split into multiple chunks");

            AtomicInteger synthesisCount = new AtomicInteger(0);
            List<byte[]> audioChunks = processor.processChunks(chunks, (chunkText, vcn) -> {
                synthesisCount.incrementAndGet();
                return createTestWavData(chunkText.length() * 10);
            }, "test-vcn");

            assertEquals(chunks.size(), audioChunks.size());
            assertEquals(chunks.size(), synthesisCount.get());

            byte[] mergedAudio = merger.merge(audioChunks);
            assertTrue(mergedAudio.length > 44, "Merged audio should have data");

            int dataSizeInHeader = (mergedAudio[40] & 0xFF) |
                    ((mergedAudio[41] & 0xFF) << 8) |
                    ((mergedAudio[42] & 0xFF) << 16) |
                    ((mergedAudio[43] & 0xFF) << 24);
            assertEquals(mergedAudio.length - 44, dataSizeInHeader);
        }

        @Test
        @DisplayName("对比：智能分块 vs 简单字节分块")
        void testSmartChunkingVsSimpleByteChunking() {
            String text = "今天天气很好。我们一起去公园散步吧！公园里有很多花，" +
                    "还有小鸟在唱歌。真是太开心了！你觉不觉得呢？" +
                    "我觉得这样的日子特别适合出门。阳光明媚，微风习习。" +
                    "希望每天都能这么美好！让我们珍惜每一个好天气吧。";

            SmartTextChunker smartChunker = new SmartTextChunker(100, 20);
            List<SmartTextChunker.TextChunk> smartChunks = smartChunker.chunk(text);

            boolean hasSentenceOrClauseBreak = smartChunks.stream()
                    .anyMatch(c -> c.type() == SmartTextChunker.ChunkType.SENTENCE_BREAK ||
                            c.type() == SmartTextChunker.ChunkType.CLAUSE_BREAK);

            assertTrue(hasSentenceOrClauseBreak,
                    "Smart chunker should split at sentence or clause boundaries");

            for (SmartTextChunker.TextChunk chunk : smartChunks) {
                assertTrue(chunk.byteLength() <= 100,
                        "Each chunk should respect byte limit");
            }
        }

        @Test
        @DisplayName("并发处理性能 - 多线程比串行快")
        void testConcurrentPerformance() throws Exception {
            SmartTextChunker chunker = new SmartTextChunker(200, 50);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append("这是性能测试文本，第").append(i + 1).append("段内容。");
            }
            String text = sb.toString();
            List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);

            ConcurrentTtsProcessor concurrentProcessor = new ConcurrentTtsProcessor(4, 0, 60);

            long startConcurrent = System.currentTimeMillis();
            List<byte[]> concurrentResults = concurrentProcessor.processChunks(chunks, (t, v) -> {
                Thread.sleep(100);
                return createTestWavData(100);
            }, "vcn");
            long concurrentTime = System.currentTimeMillis() - startConcurrent;

            assertTrue(concurrentResults.size() > 0);
            assertTrue(concurrentTime < chunks.size() * 100,
                    "Concurrent processing should be faster than serial: " + concurrentTime + "ms vs " + (chunks.size() * 100) + "ms");
        }

        private byte[] createTestWavData(int dataSize) {
            int totalSize = 44 + dataSize;
            byte[] wav = new byte[totalSize];

            wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
            wav[4] = (byte) ((totalSize - 8) & 0xFF);
            wav[5] = (byte) (((totalSize - 8) >> 8) & 0xFF);
            wav[6] = (byte) (((totalSize - 8) >> 16) & 0xFF);
            wav[7] = (byte) (((totalSize - 8) >> 24) & 0xFF);
            wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
            wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
            wav[16] = 16; wav[17] = 0; wav[18] = 0; wav[19] = 0;
            wav[20] = 1; wav[21] = 0;
            wav[22] = 1; wav[23] = 0;
            wav[24] = (byte) 0x80; wav[25] = 0x5D; wav[26] = 0; wav[27] = 0;
            wav[28] = 0; wav[29] = 0x7D; wav[30] = 0; wav[31] = 0;
            wav[32] = 2; wav[33] = 0;
            wav[34] = 16; wav[35] = 0;
            wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
            wav[40] = (byte) (dataSize & 0xFF);
            wav[41] = (byte) ((dataSize >> 8) & 0xFF);
            wav[42] = (byte) ((dataSize >> 16) & 0xFF);
            wav[43] = (byte) ((dataSize >> 24) & 0xFF);

            return wav;
        }
    }
}
