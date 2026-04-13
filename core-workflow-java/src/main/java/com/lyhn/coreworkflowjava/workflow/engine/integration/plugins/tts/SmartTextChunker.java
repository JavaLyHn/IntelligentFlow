package com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.tts;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SmartTextChunker {

    private static final int DEFAULT_MAX_BYTES = 500;
    private static final int MIN_CHUNK_BYTES = 100;

    private static final Pattern SENTENCE_BREAK = Pattern.compile(
            "[。！？；.!?;]+[\"'\u201d\u2019\u00bb\u300d\u300f\\])\u300b\u3011}]*"
    );

    private static final Pattern CLAUSE_BREAK = Pattern.compile(
            "[，、,：:；;]+[\"'\u201d\u2019\u00bb\u300d\u300f\\])\u300b\u3011}]*"
    );

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile(
            "\\n+"
    );

    private static final Pattern WHITESPACE_BREAK = Pattern.compile(
            "\\s+"
    );

    private final int maxBytes;
    private final int minChunkBytes;

    public SmartTextChunker() {
        this(DEFAULT_MAX_BYTES, MIN_CHUNK_BYTES);
    }

    public SmartTextChunker(int maxBytes, int minChunkBytes) {
        this.maxBytes = maxBytes;
        this.minChunkBytes = Math.min(minChunkBytes, maxBytes / 2);
    }

    public List<TextChunk> chunk(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            TextChunk chunk = new TextChunk(text, 0, bytes.length, ChunkType.SINGLE);
            log.debug("[SmartTextChunker] Text fits in single chunk: {} bytes", bytes.length);
            return List.of(chunk);
        }

        List<TextChunk> chunks = new ArrayList<>();
        int charOffset = 0;

        while (charOffset < text.length()) {
            int remainingBytes = getByteLength(text, charOffset, text.length());

            if (remainingBytes <= maxBytes) {
                String chunkText = text.substring(charOffset);
                chunks.add(new TextChunk(chunkText, charOffset, remainingBytes, ChunkType.LAST));
                break;
            }

            SplitResult split = findBestSplitPoint(text, charOffset);

            String chunkText = text.substring(charOffset, split.splitOffset);
            int chunkBytes = getByteLength(text, charOffset, split.splitOffset);

            chunks.add(new TextChunk(chunkText, charOffset, chunkBytes, split.type));
            charOffset = split.splitOffset;

            while (charOffset < text.length() && Character.isWhitespace(text.charAt(charOffset))) {
                charOffset++;
            }
        }

        chunks = mergeSmallChunks(chunks);

        log.info("[SmartTextChunker] Split text into {} chunks (total {} bytes)", chunks.size(), bytes.length);
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk c = chunks.get(i);
            log.debug("[SmartTextChunker] Chunk {}: type={}, bytes={}, preview='{}'",
                    i, c.type(), c.byteLength(),
                    c.text().substring(0, Math.min(c.text().length(), 30)));
        }

        return chunks;
    }

    private SplitResult findBestSplitPoint(String text, int startOffset) {
        int searchEnd = findMaxCharOffsetForBytes(text, startOffset, maxBytes);

        SplitResult sentenceSplit = findPatternSplit(text, startOffset, searchEnd, SENTENCE_BREAK, ChunkType.SENTENCE_BREAK);
        if (sentenceSplit != null) return sentenceSplit;

        SplitResult paragraphSplit = findPatternSplit(text, startOffset, searchEnd, PARAGRAPH_BREAK, ChunkType.PARAGRAPH_BREAK);
        if (paragraphSplit != null) return paragraphSplit;

        SplitResult clauseSplit = findPatternSplit(text, startOffset, searchEnd, CLAUSE_BREAK, ChunkType.CLAUSE_BREAK);
        if (clauseSplit != null) return clauseSplit;

        SplitResult whitespaceSplit = findPatternSplit(text, startOffset, searchEnd, WHITESPACE_BREAK, ChunkType.WHITESPACE_BREAK);
        if (whitespaceSplit != null) return whitespaceSplit;

        int fallbackOffset = findUtf8SafeBoundary(text, searchEnd);
        return new SplitResult(fallbackOffset, ChunkType.FORCED);
    }

    private SplitResult findPatternSplit(String text, int startOffset, int maxOffset, Pattern pattern, ChunkType type) {
        String searchRegion = text.substring(startOffset, maxOffset);
        Matcher matcher = pattern.matcher(searchRegion);

        int lastMatchEnd = -1;
        while (matcher.find()) {
            lastMatchEnd = matcher.end();
        }

        if (lastMatchEnd > 0) {
            int splitOffset = startOffset + lastMatchEnd;
            int chunkBytes = getByteLength(text, startOffset, splitOffset);

            if (chunkBytes >= minChunkBytes) {
                return new SplitResult(splitOffset, type);
            }
        }

        return null;
    }

    private int findMaxCharOffsetForBytes(String text, int startOffset, int targetBytes) {
        int low = startOffset;
        int high = text.length();
        int result = startOffset + 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            int bytes = getByteLength(text, startOffset, mid);

            if (bytes <= targetBytes) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return result;
    }

    private int findUtf8SafeBoundary(String text, int offset) {
        if (offset >= text.length()) return text.length();

        while (offset > 0 && isContinuationByte(text.charAt(offset))) {
            offset--;
        }

        return Math.max(offset, 1);
    }

    private boolean isContinuationByte(char c) {
        return (c & 0xC0) == 0x80;
    }

    private List<TextChunk> mergeSmallChunks(List<TextChunk> chunks) {
        if (chunks.size() <= 1) return chunks;

        List<TextChunk> merged = new ArrayList<>();
        TextChunk current = chunks.get(0);

        for (int i = 1; i < chunks.size(); i++) {
            TextChunk next = chunks.get(i);

            if (current.byteLength() + next.byteLength() <= maxBytes) {
                String mergedText = current.text() + " " + next.text();
                current = new TextChunk(mergedText, current.charOffset(),
                        current.byteLength() + next.byteLength(),
                        ChunkType.MERGED);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        return merged;
    }

    private int getByteLength(String text, int start, int end) {
        return text.substring(start, end).getBytes(StandardCharsets.UTF_8).length;
    }

    public record TextChunk(String text, int charOffset, int byteLength, ChunkType type) {}

    public enum ChunkType {
        SINGLE,
        SENTENCE_BREAK,
        PARAGRAPH_BREAK,
        CLAUSE_BREAK,
        WHITESPACE_BREAK,
        FORCED,
        LAST,
        MERGED
    }

    private record SplitResult(int splitOffset, ChunkType type) {}
}
