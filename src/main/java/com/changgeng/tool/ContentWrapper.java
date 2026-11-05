package com.changgeng.tool;

import com.changgeng.model.ai.ThreadRunStreamReq;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collections;
import java.util.List;

public class ContentWrapper {
    private final List<ThreadRunStreamReq.ContentItemDTO> items;

    @JsonCreator
    public ContentWrapper(Object value) {
        if (value instanceof List<?>) {
            this.items = (List<ThreadRunStreamReq.ContentItemDTO>) value;
        } else if (value instanceof ThreadRunStreamReq.ContentItemDTO) {
            this.items = Collections.singletonList((ThreadRunStreamReq.ContentItemDTO) value);
        } else {
            this.items = Collections.emptyList();
        }
    }

    @JsonValue
    public List<ThreadRunStreamReq.ContentItemDTO> getItems() {
        return items;
    }
}
