package com.changgeng.controller;

import com.changgeng.service.DeerFlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@Slf4j
public class DeerFlowTestController {

    private final DeerFlowService deerFlowService;

    public DeerFlowTestController(DeerFlowService deerFlowService) {
        this.deerFlowService = deerFlowService;
    }

    @PostMapping("/test/deerflow/chat")
    public Mono<String> testChat(@RequestBody Map param) {
        String question = (String) param.get("content");
        log.error("content {}" , question);
        // 1. 创建会话
        return deerFlowService.getOrCreateThreadId("测试")
                .flatMap(threadResp -> {
                    String tid = threadResp.toString();
                    log.error("会话id {}" , tid);
                    // 2. 提问，拿到回答
                    return deerFlowService.askQuestion(tid, question);
                });
    }
}
