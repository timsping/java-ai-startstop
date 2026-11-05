package com.changgeng.service;

import com.alibaba.fastjson.JSON;
import com.changgeng.model.ai.AiMessageItem;
import com.changgeng.model.ai.ThreadCacheItem;
import com.changgeng.model.ai.ThreadCreateReq;
import com.changgeng.model.ai.ThreadCreateResp;
import com.changgeng.model.ai.ThreadRunStreamReq;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class DeerFlowService {

    private final WebClient deerFlowWebClient;
    private final ObjectMapper objectMapper;

    private static final String X_CSRF_TOKEN = "EH1z06aLSTILiFYW30EmqNyKf4RBzKsrI0vBlC_K98L7WCia29Kbk-2hwJW1IgR2KNC0faKb4_xNBFbDYbq9wQ";
    private static final String COOKIE = "locale=zh-CN; deerflow_session_persistent=1; access_token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MjA2MjNiOS1kOGIzLTQ1NzEtOWZkMS0xZjFmYWJkNjYxODEiLCJleHAiOjE3OTQxMjQzMDMsImlhdCI6MTc5MzUxOTUwMywidmVyIjowfQ.6xamFzCXd727bW2NVmarAxv4Q-uNXsfUbTXaAY9DjwI; csrf_token=EH1zO6aLSTILiFYW3OEmqNyKf4RBzKsrIOvBlC_K98l7WCia29Kbk-2hwJW1IgR2KNC0faKb4_xNBFbDYbq9wQ";
    private final AtomicReference<ThreadCacheItem> threadCache = new AtomicReference<>();
    private static final long EXPIRE_HOUR = 24;

    public DeerFlowService(WebClient deerFlowWebClient, ObjectMapper objectMapper) {
        this.deerFlowWebClient = deerFlowWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取可用threadId，24h内复用，超时重新创建
     */
    public Mono<String> getOrCreateThreadId(String title) {
        ThreadCacheItem cache = threadCache.get();
        LocalDateTime now = LocalDateTime.now();
        if (cache != null) {
            long hours = ChronoUnit.HOURS.between(cache.getCreateTime(), now);
            if (hours < EXPIRE_HOUR) {
                return Mono.just(cache.getThreadId());
            }
        }
        return createThread(title)
                .map(resp -> {
                    ThreadCacheItem newItem = new ThreadCacheItem();
                    // 【BUG修复】DTO字段是id，不是thread_id
                    newItem.setThreadId(resp.getThread_id());
                    newItem.setCreateTime(now);
                    threadCache.set(newItem);
                    return resp.getThread_id();
                });
    }

    /**
     * 创建会话
     */
    public Mono<ThreadCreateResp> createThread(String title) {
        ThreadCreateReq req = new ThreadCreateReq();
        req.setTitle(title);
        return deerFlowWebClient.post()
                .uri("/api/threads")
                .header("X-CSRF-Token", X_CSRF_TOKEN)
                .header("Cookie", COOKIE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .onStatus(status -> status.value() == 403,
                        resp -> Mono.error(new RuntimeException("DeerFlow 403，CSRF凭证失效")))
                .bodyToMono(ThreadCreateResp.class);
    }

    /**
     * 发送问题，纯响应式，无block
     */
    public Mono<String> askQuestion(String threadId, String question) {
        ThreadRunStreamReq req = new ThreadRunStreamReq();
        ThreadRunStreamReq.InputDTO input = new ThreadRunStreamReq.InputDTO();

        ThreadRunStreamReq.HumanMessageDTO humanMsg = new ThreadRunStreamReq.HumanMessageDTO();
        humanMsg.setId("local‑human‑" + UUID.randomUUID());
        ThreadRunStreamReq.ContentItemDTO contentItem = new ThreadRunStreamReq.ContentItemDTO();
        contentItem.setText(question);
        humanMsg.setContent(Arrays.asList(contentItem));
        input.setMessages(Arrays.asList(humanMsg));

        req.setInput(input);
        ThreadRunStreamReq.ConfigDTO config = new ThreadRunStreamReq.ConfigDTO();
        config.setRecursionLimit(1000);
        req.setConfig(config);

        Flux<String> sseFlux = deerFlowWebClient.post()
                .uri("/api/threads/{threadId}/runs/stream", threadId)
                .header("X-CSRF-Token", X_CSRF_TOKEN)
                .header("Cookie", COOKIE)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(req)
                .retrieve()
                .onStatus(status -> status.value() == 403,
                        resp -> Mono.error(new RuntimeException("DeerFlow 403，CSRF凭证失效")))
                .bodyToFlux(String.class);

        return sseFlux
                .doOnNext(line -> {
                    if (line.startsWith("data:")) {
                        String jsonStr = line.substring("data:".length()).trim();
                        System.out.println("SSE原始行：" + jsonStr);
                    }
                })
                .doOnComplete(() -> System.out.println("SSE流接收完毕，查询消息拿结果"))
                // ✅ 修复关键：then直接传Mono，不要套fromCallable
                .then(fetchAiAnswerAfterStream(threadId));
    }


    public Mono<String> fetchAiAnswerAfterStream(String threadId) {
        return deerFlowWebClient.get()
                .uri("/api/threads/{threadId}/messages", threadId)
                .header("X-CSRF-Token", X_CSRF_TOKEN)
                .header("Cookie", COOKIE)
                .retrieve()
                .onStatus(status -> status.value() == 403,
                        resp -> Mono.error(new RuntimeException("DeerFlow 403，CSRF凭证失效")))
                .bodyToMono(String.class)
                .map(jsonStr -> {
                    try {
                        JsonNode root = objectMapper.readTree(jsonStr);
                        // 倒序遍历，拿最新消息
                        for (int i = root.size() - 1; i >= 0; i--) {
                            JsonNode msg = root.get(i);
                            String eventType = msg.get("event_type").asText();
                            // AI回复的标记
                            if ("llm.ai.response".equals(eventType)) {
                                JsonNode contentObj = msg.get("content");
                                JsonNode sb = contentObj.get("content");
                              /*  StringBuilder sb = new StringBuilder();
                                for (JsonNode block : contentArr) {
                                    if ("text".equals(block.get("type").asText())) {
                                        sb.append(block.get("text").asText());
                                    }
                                }*/
                                return sb.toString();
                            }
                        }
                        return "未找到AI回复";
                    } catch (Exception e) {
                        log.error("解析消息失败", e);
                        return "解析失败";
                    }
                });
    }

}
