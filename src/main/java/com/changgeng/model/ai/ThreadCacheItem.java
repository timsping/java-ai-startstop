package com.changgeng.model.ai;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ThreadCacheItem {
    private String threadId;
    private LocalDateTime createTime;
}
