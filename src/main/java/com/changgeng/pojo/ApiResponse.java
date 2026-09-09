package com.changgeng.pojo;

import lombok.Data;

@Data
public class ApiResponse {
    private Integer code;
    private Object data;
    private Boolean success;
    private String message;
}
