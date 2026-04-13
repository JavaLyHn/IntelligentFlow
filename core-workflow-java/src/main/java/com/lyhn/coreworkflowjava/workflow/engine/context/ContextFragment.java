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
public class ContextFragment implements Serializable {

    private static final long serialVersionUID = 1L;

    private String entryId;

    private String sessionId;

    private int startOffset;

    private int endOffset;

    private String content;

    public static ContextFragment of(String entryId, String sessionId,
                                     int startOffset, int endOffset, String content) {
        return ContextFragment.builder()
                .entryId(entryId)
                .sessionId(sessionId)
                .startOffset(startOffset)
                .endOffset(endOffset)
                .content(content)
                .build();
    }
}
