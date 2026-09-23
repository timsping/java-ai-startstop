package com.changgeng.pojo;

import lombok.Data;

@Data
public class SourceRecord {
    private String eventName;
    private String childEventName;
    private String childEventName1;

    private String eventCode;
    private String childEventCode;
    private String childEventCode1;

    /** 根节点id */
    private Long eventId;
    /** 一级子节点id */
    private Long childEventId;
    /** 二级子节点id */
    private Long childEventId1;

    private String eventStatus;
}
