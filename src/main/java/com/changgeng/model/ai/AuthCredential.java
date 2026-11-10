package com.changgeng.model.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCredential {
    private String accessToken;
    private String csrfToken;
    private LocalDateTime loginTime;
    /** 拼接完成的Cookie请求头字符串 */
    private String cookieHeader;
}
