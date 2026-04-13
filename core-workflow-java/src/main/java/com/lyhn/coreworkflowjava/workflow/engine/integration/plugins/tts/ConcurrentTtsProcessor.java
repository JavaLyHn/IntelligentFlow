package com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.tts;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ConcurrentTtsProcessor {

    private static final int DEFAULT_CONCURRENCY = 4;
    private static final long DEFAULT_CHUNK_DELAY_MS = 200;
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    private final int maxConcurrency;
    private final long chunkDelayMs;
    private final long timeoutSeconds;

    public ConcurrentTtsProcessor() {
        this(DEFAULT_CONCURRENCY, DEFAULT_CHUNK_DELAY_MS, DEFAULT_TIMEOUT_SECONDS);
    }

    public ConcurrentTtsProcessor(int maxConcurrency, long chunkDelayMs, long timeoutSeconds) {
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.chunkDelayMs = chunkDelayMs;
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<byte[]> processChunks(List<SmartTextChunker.TextChunk> chunks,
                                      TtsSynthesisFunction synthesisFunction,
                                      String vcn) throws Exception {
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        if (chunks.size() == 1) {
            log.info("[ConcurrentTtsProcessor] Single chunk, processing directly");
            byte[] audio = synthesisFunction.synthesize(chunks.get(0).text(), vcn);
            return List.of(audio);
        }

        log.info("[ConcurrentTtsProcessor] Processing {} chunks with concurrency={}",
                chunks.size(), maxConcurrency);

        int actualConcurrency = Math.min(maxConcurrency, chunks.size());
        ExecutorService executor = Executors.newFixedThreadPool(actualConcurrency, r -> {
            Thread t = new Thread(r, "tts-concurrent-worker");
            t.setDaemon(true);
            return t;
        });

        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        try {
            Map<Integer, CompletableFuture<byte[]>> futureMap = new LinkedHashMap<>();

            for (int i = 0; i < chunks.size(); i++) {
                final int index = i;
                final SmartTextChunker.TextChunk chunk = chunks.get(i);

                CompletableFuture<byte[]> future = new CompletableFuture<>();

                long delayMs = (i / actualConcurrency) * chunkDelayMs;

                executor.submit(() -> {
                    try {
                        if (delayMs > 0) {
                            Thread.sleep(delayMs);
                        }

                        log.info("[ConcurrentTtsProcessor] Processing chunk {}/{}, type={}, bytes={}",
                                index + 1, chunks.size(), chunk.type(), chunk.byteLength());

                        long startTime = System.currentTimeMillis();
                        byte[] audioData = synthesisFunction.synthesize(chunk.text(), vcn);
                        long elapsed = System.currentTimeMillis() - startTime;

                        int completed = completedCount.incrementAndGet();
                        log.info("[ConcurrentTtsProcessor] Chunk {}/{} completed in {}ms (total progress: {}/{})",
                                index + 1, chunks.size(), elapsed, completed, chunks.size());

                        future.complete(audioData);
                    } catch (Exception e) {
                        failedCount.incrementAndGet();
                        log.error("[ConcurrentTtsProcessor] Chunk {} failed: {}", index + 1, e.getMessage());
                        future.completeExceptionally(e);
                    }
                });

                futureMap.put(i, future);
            }

            List<byte[]> results = new ArrayList<>();
            for (Map.Entry<Integer, CompletableFuture<byte[]>> entry : futureMap.entrySet()) {
                try {
                    byte[] audioData = entry.getValue().get(timeoutSeconds, TimeUnit.SECONDS);
                    results.add(audioData);
                } catch (TimeoutException e) {
                    log.error("[ConcurrentTtsProcessor] Chunk {} timed out after {}s",
                            entry.getKey() + 1, timeoutSeconds);
                    throw new RuntimeException("TTS chunk " + (entry.getKey() + 1) + " timed out");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    log.error("[ConcurrentTtsProcessor] Chunk {} execution failed: {}",
                            entry.getKey() + 1, cause.getMessage());
                    throw new RuntimeException("TTS chunk " + (entry.getKey() + 1) + " failed: " + cause.getMessage(), cause);
                }
            }

            log.info("[ConcurrentTtsProcessor] All {} chunks processed. Completed: {}, Failed: {}",
                    chunks.size(), completedCount.get(), failedCount.get());

            return results;

        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("[ConcurrentTtsProcessor] Executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public ProcessingStats getProcessingStats(long startTimeMs, int totalChunks, int completedChunks, int failedChunks) {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return new ProcessingStats(totalChunks, completedChunks, failedChunks, elapsed,
                totalChunks > 0 ? (long) completedChunks * 1000 / Math.max(elapsed, 1) : 0);
    }

    @FunctionalInterface
    public interface TtsSynthesisFunction {
        byte[] synthesize(String text, String vcn) throws Exception;
    }

    public record ProcessingStats(
            int totalChunks,
            int completedChunks,
            int failedChunks,
            long elapsedMs,
            long chunksPerSecond
    ) {}
}
