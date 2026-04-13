package com.lyhn.coreworkflowjava.workflow.engine.context;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ContextManager {

    private final ContextStorageService storageService;
    private final int compressionThreshold;
    private final int summaryMaxLength;

    public ContextManager(ContextStorageService storageService,
                          int compressionThreshold,
                          int summaryMaxLength) {
        this.storageService = storageService;
        this.compressionThreshold = compressionThreshold;
        this.summaryMaxLength = summaryMaxLength;
    }

    public ContextManager(ContextStorageService storageService) {
        this(storageService, 2000, 200);
    }

    public ContextEntry addContext(String sessionId, String type, String content) {
        log.info("[ContextManager] Adding context: sessionId={}, type={}, contentSize={}",
                sessionId, type, content != null ? content.length() : 0);

        CompressedContext context = getOrCreateContext(sessionId);

        String summary;
        String storagePath = null;
        boolean externalized = false;
        long originalSize = content != null ? content.length() : 0;

        if (content != null && content.length() > compressionThreshold) {
            summary = compress(content);
            storagePath = storageService.externalizeToMinIO(sessionId,
                    generateEntryId(sessionId, context.getEntryCount()), content);
            externalized = true;
            log.info("[ContextManager] Context externalized: sessionId={}, originalSize={}, summarySize={}, path={}",
                    sessionId, originalSize, summary.length(), storagePath);
        } else {
            summary = content != null ? content : "";
        }

        ContextEntry entry = ContextEntry.builder()
                .entryId(generateEntryId(sessionId, context.getEntryCount()))
                .sessionId(sessionId)
                .type(type)
                .summary(summary)
                .storagePath(storagePath)
                .originalSize(originalSize)
                .compressedSize(summary.length())
                .externalized(externalized)
                .createdAt(System.currentTimeMillis())
                .build();

        context.addEntry(entry);

        storageService.saveEntrySummary(entry);
        storageService.saveCompressedContext(context);

        return entry;
    }

    public String retrieveFullContent(String sessionId, String entryId) {
        log.info("[ContextManager] Retrieving full content: sessionId={}, entryId={}", sessionId, entryId);

        CompressedContext context = storageService.loadCompressedContext(sessionId);
        if (context == null) {
            log.warn("[ContextManager] Context not found: sessionId={}", sessionId);
            return null;
        }

        ContextEntry entry = context.findEntry(entryId);
        if (entry == null) {
            log.warn("[ContextManager] Entry not found: entryId={}", entryId);
            return null;
        }

        if (!entry.isExternalized()) {
            return entry.getSummary();
        }

        String fullContent = storageService.readFromMinIO(entry.getStoragePath());
        if (fullContent == null) {
            log.warn("[ContextManager] Failed to read externalized content, falling back to summary");
            return entry.getSummary();
        }

        return fullContent;
    }

    public ContextFragment retrieveFragment(String sessionId, String entryId,
                                             int startOffset, int endOffset) {
        log.info("[ContextManager] Retrieving fragment: sessionId={}, entryId={}, range=[{},{}]",
                sessionId, entryId, startOffset, endOffset);

        CompressedContext context = storageService.loadCompressedContext(sessionId);
        if (context == null) {
            return null;
        }

        ContextEntry entry = context.findEntry(entryId);
        if (entry == null) {
            return null;
        }

        if (!entry.isExternalized()) {
            String content = entry.getSummary();
            int safeStart = Math.max(0, startOffset);
            int safeEnd = Math.min(content.length(), endOffset);
            if (safeStart >= safeEnd) {
                return ContextFragment.of(entryId, sessionId, safeStart, safeStart, "");
            }
            return ContextFragment.of(entryId, sessionId, safeStart, safeEnd,
                    content.substring(safeStart, safeEnd));
        }

        return storageService.readFragmentFromMinIO(
                entry.getStoragePath(), sessionId, entryId, startOffset, endOffset);
    }

    public List<ContextFragment> searchFragments(String sessionId, String keyword,
                                                  int contextWindow) {
        log.info("[ContextManager] Searching fragments: sessionId={}, keyword={}", sessionId, keyword);

        CompressedContext context = storageService.loadCompressedContext(sessionId);
        if (context == null) {
            return List.of();
        }

        java.util.List<ContextFragment> fragments = new java.util.ArrayList<>();

        for (ContextEntry entry : context.getEntries()) {
            if (!entry.isExternalized()) {
                searchInContent(entry, keyword, contextWindow, fragments);
            } else {
                String fullContent = storageService.readFromMinIO(entry.getStoragePath());
                if (fullContent != null) {
                    searchInFullContent(entry, fullContent, keyword, contextWindow, fragments);
                }
            }
        }

        return fragments;
    }

    public String getCompressedSummary(String sessionId) {
        CompressedContext context = storageService.loadCompressedContext(sessionId);
        if (context == null) {
            return "";
        }
        return context.buildContextSummary();
    }

    public CompressedContext getCompressedContext(String sessionId) {
        return storageService.loadCompressedContext(sessionId);
    }

    public boolean removeEntry(String sessionId, String entryId) {
        log.info("[ContextManager] Removing entry: sessionId={}, entryId={}", sessionId, entryId);

        CompressedContext context = storageService.loadCompressedContext(sessionId);
        if (context == null) {
            return false;
        }

        ContextEntry entry = context.findEntry(entryId);
        if (entry == null) {
            return false;
        }

        if (entry.isExternalized()) {
            storageService.deleteFromMinIO(entry.getStoragePath());
        }

        context.removeEntry(entryId);
        storageService.saveCompressedContext(context);

        return true;
    }

    public double getCompressionRatio(String sessionId) {
        CompressedContext context = storageService.loadCompressedContext(sessionId);
        if (context == null) {
            return 0.0;
        }
        return context.getCompressionRatio();
    }

    public long getTotalOriginalSize(String sessionId) {
        CompressedContext context = storageService.loadCompressedContext(sessionId);
        return context != null ? context.getTotalOriginalSize() : 0;
    }

    public long getTotalCompressedSize(String sessionId) {
        CompressedContext context = storageService.loadCompressedContext(sessionId);
        return context != null ? context.getTotalCompressedSize() : 0;
    }

    private CompressedContext getOrCreateContext(String sessionId) {
        CompressedContext context = storageService.loadCompressedContext(sessionId);
        if (context == null) {
            context = CompressedContext.builder()
                    .sessionId(sessionId)
                    .compressionThreshold(compressionThreshold)
                    .build();
        }
        return context;
    }

    private String compress(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        String firstNChars = content.length() > summaryMaxLength
                ? content.substring(0, summaryMaxLength) + "..."
                : content;

        int totalLines = content.split("\n").length;
        String lineInfo = totalLines > 1 ? " (" + totalLines + " lines)" : "";

        return firstNChars + lineInfo;
    }

    private String generateEntryId(String sessionId, int entryCount) {
        return sessionId + "-" + entryCount;
    }

    private void searchInContent(ContextEntry entry, String keyword, int contextWindow,
                                 java.util.List<ContextFragment> fragments) {
        String content = entry.getSummary();
        int index = 0;
        while ((index = content.indexOf(keyword, index)) != -1) {
            int start = Math.max(0, index - contextWindow);
            int end = Math.min(content.length(), index + keyword.length() + contextWindow);
            fragments.add(ContextFragment.of(entry.getEntryId(), entry.getSessionId(),
                    start, end, content.substring(start, end)));
            index += keyword.length();
        }
    }

    private void searchInFullContent(ContextEntry entry, String fullContent,
                                     String keyword, int contextWindow,
                                     java.util.List<ContextFragment> fragments) {
        int index = 0;
        while ((index = fullContent.indexOf(keyword, index)) != -1) {
            int start = Math.max(0, index - contextWindow);
            int end = Math.min(fullContent.length(), index + keyword.length() + contextWindow);
            fragments.add(ContextFragment.of(entry.getEntryId(), entry.getSessionId(),
                    start, end, fullContent.substring(start, end)));
            index += keyword.length();
        }
    }
}
