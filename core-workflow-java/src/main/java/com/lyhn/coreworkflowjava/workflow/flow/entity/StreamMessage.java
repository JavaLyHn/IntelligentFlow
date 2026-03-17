package com.lyhn.coreworkflowjava.workflow.flow.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StreamMessage {
    private long sequence;       // 序号
    private String content;      // 内容
    private long timestamp;      // 时间戳
    private int retryCount;      // 重试次数
    private boolean isEnd;       // 是否结束标记
}
