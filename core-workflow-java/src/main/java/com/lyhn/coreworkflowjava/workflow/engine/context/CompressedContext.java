package com.lyhn.coreworkflowjava.workflow.engine.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompressedContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;

    @Builder.Default
    private List<ContextEntry> entries = new ArrayList<>();

    @Builder.Default
    private long totalOriginalSize = 0;

    @Builder.Default
    private long totalCompressedSize = 0;

    @Builder.Default
    private int compressionThreshold = 2000;

    public void addEntry(ContextEntry entry) {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        entries.add(entry);
        totalOriginalSize += entry.getOriginalSize();
        totalCompressedSize += entry.getCompressedSize();
    }

    public void removeEntry(String entryId) {
        if (entries == null) return;
        entries.removeIf(e -> {
            if (e.getEntryId().equals(entryId)) {
                totalOriginalSize -= e.getOriginalSize();
                totalCompressedSize -= e.getCompressedSize();
                return true;
            }
            return false;
        });
    }

    public ContextEntry findEntry(String entryId) {
        if (entries == null) return null;
        return entries.stream()
                .filter(e -> e.getEntryId().equals(entryId))
                .findFirst()
                .orElse(null);
    }

    public List<ContextEntry> findEntriesByType(String type) {
        if (entries == null) return new ArrayList<>();
        return entries.stream()
                .filter(e -> type.equals(e.getType()))
                .toList();
    }

    public double getCompressionRatio() {
        if (totalOriginalSize == 0) return 0.0;
        return (double) totalCompressedSize / totalOriginalSize;
    }

    public int getEntryCount() {
        return entries != null ? entries.size() : 0;
    }

    public String buildContextSummary() {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContextEntry entry : entries) {
            sb.append("[").append(entry.getType()).append("] ");
            if (entry.isExternalized()) {
                sb.append(entry.getSummary());
                sb.append(" (ref: ").append(entry.getStoragePath()).append(")");
            } else {
                sb.append(entry.getSummary());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
