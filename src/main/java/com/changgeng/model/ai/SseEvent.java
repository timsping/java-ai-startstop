package com.changgeng.model.ai;

import lombok.Data;

// SSE单条事件解析对象
@Data
public class SseEvent {
    private String event;
    private String data;
}
