package com.changgeng.model;

import lombok.Data;

import java.util.List;

@Data
public class StartStopQueryDTO {
    private String startTime;
    private String endTime;
    private Integer nodeId;
    private List<Integer> eventIds;
    private String resultId;
}
