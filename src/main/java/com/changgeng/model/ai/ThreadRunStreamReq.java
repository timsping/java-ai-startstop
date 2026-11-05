package com.changgeng.model.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ThreadRunStreamReq {
    private InputDTO input;
    private ConfigDTO config;

    @Data
    public static class InputDTO {
        private List<HumanMessageDTO> messages;
    }

    @Data
    public static class HumanMessageDTO {
        private String type = "human";
        private String id;
        private List<ContentItemDTO> content;
        @JsonProperty("additional_kwargs")
        private Map<String,Object> additionalKwargs = new HashMap<>();
    }

    @Data
    public static class ContentItemDTO {
        private String type = "text";
        private String text;
    }

    @Data
    public static class ConfigDTO {
        @JsonProperty("recursion_limit")
        private Integer recursionLimit = 1000;
    }
}
