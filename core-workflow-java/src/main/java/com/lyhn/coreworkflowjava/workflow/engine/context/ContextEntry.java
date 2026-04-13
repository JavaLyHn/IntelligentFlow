package com.lyhn.coreworkflowjava.workflow.engine.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String entryId;

    private String sessionId;

    private String type;

    private String summary;

    private String storagePath;

    @Builder.Default
    private long originalSize = 0;

    @Builder.Default
    private long compressedSize = 0;

    @Builder.Default
    private boolean externalized = false;

    private String metadata;

    @Builder.Default
    private long createdAt = System.currentTimeMillis();

    public static ContextEntry of(String sessionId, String type, String content, String summary,
                                  String storagePath, long originalSize) {
        return ContextEntry.builder()
                .entryId(generateEntryId())
                .sessionId(sessionId)
                .type(type)
                .summary(summary)
                .storagePath(storagePath)
                .originalSize(originalSize)
                .compressedSize(summary != null ? summary.length() : 0)
                .externalized(storagePath != null && !storagePath.isEmpty())
                .createdAt(System.currentTimeMillis())
                .build();
    }

    private static String generateEntryId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
