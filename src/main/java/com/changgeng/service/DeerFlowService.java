package com.changgeng.service;

import com.changgeng.model.ai.AuthCredential;
import com.changgeng.model.ai.ThreadCacheItem;
import com.changgeng.model.ai.ThreadCreateReq;
import com.changgeng.model.ai.ThreadCreateResp;
import com.changgeng.model.ai.ThreadRunStreamReq;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class DeerFlowService {

    @Value("${deelflow.userName:18569004224@163.com}")
    private String userName;
    @Value("${deelflow.userPassword:timsping@2026}")
    private String userPassword;

    /** 会话有效期7天，和deerflow expires_in:604800保持一致 */
    private static final long SESSION_VALID_HOUR = 7 * 24;
    private static final long EXPIRE_HOUR = 24;

    private final WebClient deerFlowWebClient;
    private final ObjectMapper objectMapper;

    /** thread缓存：24h复用threadId */
    private final AtomicReference<ThreadCacheItem> threadCache = new AtomicReference<>();
    /** 登录凭证缓存：access_token、csrf_token、登录时间、cookieHeader */
    private final AtomicReference<AuthCredential> authCredentialCache = new AtomicReference<>();


    public DeerFlowService(WebClient deerFlowWebClient, ObjectMapper objectMapper) {
        this.deerFlowWebClient = deerFlowWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取可用threadId，24h内复用，超时重新创建
     */
    public Mono<String> getOrCreateThreadId(String title) {
        log.info("[DeerFlow] getOrCreateThreadId 进入，title={}", title);
        ThreadCacheItem cache = threadCache.get();
        LocalDateTime now = LocalDateTime.now();
        if (false && cache != null) {
            long hours = ChronoUnit.HOURS.between(cache.getCreateTime(), now);
            log.info("[DeerFlow] thread缓存命中，cache.threadId={}, 已存活小时={}, 允许最大存活小时={}",
                    cache.getThreadId(), hours, EXPIRE_HOUR);
            if (hours < EXPIRE_HOUR) {
                log.info("[DeerFlow] thread未过期，直接复用threadId={}", cache.getThreadId());
                return Mono.just(cache.getThreadId());
            } else {
                log.info("[DeerFlow] thread缓存已过期，需要新建会话");
            }
        } else {
            log.info("[DeerFlow] thread缓存为空，需要新建会话");
        }
        return createThread(title)
                .map(resp -> {
                    ThreadCacheItem newItem = new ThreadCacheItem();
                    // 【BUG修复】DTO字段是id，不是thread_id
                    newItem.setThreadId(resp.getThread_id());
                    newItem.setCreateTime(now);
                    threadCache.set(newItem);
                    log.info("[DeerFlow] createThread成功，新建threadId={}", resp.getThread_id());
                    return resp.getThread_id();
                });
    }

    /**
     * 登录：调用 /api/v1/auth/login/local，表单提交password模式，从Set‑Cookie头解析token
     * WebClient已全局配置baseUrl，这里只用写相对路径
     */
    public Mono<AuthCredential> login() {
        log.info("[DeerFlow] login 开始执行，username={}", userName);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", userName);
        form.add("password", userPassword);
        form.add("remember_me", "true");

        return deerFlowWebClient.post()
                .uri("/api/v1/auth/login/local")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .exchangeToMono(response -> {
                    log.info("[DeerFlow] login接口响应statusCode={}", response.statusCode());
                    if (!response.statusCode().is2xxSuccessful()) {
                        return Mono.error(new RuntimeException("DeerFlow登录失败，status=" + response.statusCode()));
                    }
                    // 从response cookies拿Set‑Cookie
                    HttpCookie accessCookie = response.cookies().getFirst("access_token");
                    HttpCookie csrfCookie = response.cookies().getFirst("csrf_token");

                    String accessToken = null;
                    String csrfToken = null;
                    if (accessCookie != null) {
                        accessToken = accessCookie.getValue();
                    }
                    if (csrfCookie != null) {
                        csrfToken = csrfCookie.getValue();
                    }
                    if (accessToken == null || csrfToken == null) {
                        log.error("[DeerFlow] 登录响应头Set‑Cookie缺失，access_token={}, csrf_token={}",
                                accessToken, csrfToken);
                        return Mono.error(new RuntimeException("登录响应头Set‑Cookie未返回access_token/csrf_token"));
                    }
                    log.info("[DeerFlow] 成功解析cookie，accessToken={}, csrfToken={}", accessToken, csrfToken);

                    AuthCredential cred = new AuthCredential();
                    cred.setAccessToken(accessToken);
                    cred.setCsrfToken(csrfToken);
                    cred.setLoginTime(LocalDateTime.now());
                    // 登录完成直接拼接cookieHeader赋值
                    String cookieHeader = "locale=zh-CN; deerflow_session_persistent=1; access_token="
                            + accessToken
                            + "; csrf_token="
                            + csrfToken;
                    cred.setCookieHeader(cookieHeader);

                    authCredentialCache.set(cred);
                    log.info("[DeerFlow] 登录成功，凭证已放入缓存，loginTime={}, sessionValidHour={}, cookieHeader={}",
                            cred.getLoginTime(), SESSION_VALID_HOUR, cred.getCookieHeader());
                    return Mono.just(cred);
                });
    }

    /**
     * 获取有效凭证，过期自动重登录，cookieHeader已内部组装好
     */
    private Mono<AuthCredential> getValidCredential() {
        AuthCredential cred = authCredentialCache.get();
        LocalDateTime now = LocalDateTime.now();
        Mono<AuthCredential> credMono;

        if (cred != null) {
            long hours = ChronoUnit.HOURS.between(cred.getLoginTime(), now);
            log.info("[DeerFlow] getValidCredential:缓存凭证存在，距离登录小时={}, 最大允许小时={}",
                    hours, SESSION_VALID_HOUR);
            if (hours < SESSION_VALID_HOUR) {
                log.info("[DeerFlow] 凭证未过期，复用内存缓存凭证，accessToken={}", cred.getAccessToken());
                credMono = Mono.just(cred);
            } else {
                log.warn("[DeerFlow] 凭证已过期，清空缓存，执行重新登录");
                authCredentialCache.set(null);
                credMono = login();
            }
        } else {
            log.info("[DeerFlow] getValidCredential:内存凭证缓存为空，执行登录");
            credMono = login();
        }
        return credMono;
    }

    /**
     * 创建会话
     */
    public Mono<ThreadCreateResp> createThread(String title) {
        log.info("[DeerFlow] createThread 进入，title={}", title);
        return getValidCredential().flatMap(cred -> {
            ThreadCreateReq req = new ThreadCreateReq();
            req.setTitle(title);
            log.info("[DeerFlow] 准备调用 /api/threads 创建会话，title={}, X‑CSRF‑Token={}, CookieHeader={}",
                    title, cred.getCsrfToken(), cred.getCookieHeader());
            return deerFlowWebClient.post()
                    .uri("/api/threads")
                    .header("X-CSRF-Token", cred.getCsrfToken())
                    .header(HttpHeaders.COOKIE, cred.getCookieHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .onStatus(status -> status.value() == 403, resp -> {
                        log.info("[DeerFlow] 创建会话收到403，清空凭证缓存");
                        authCredentialCache.set(null);
                        return Mono.error(new RuntimeException("DeerFlow 403，CSRF凭证失效，已清空缓存"));
                    })
                    .bodyToMono(ThreadCreateResp.class);
        });
    }

    /**
     * 发送问题，纯响应式，无block
     */
    public Mono<String> askQuestion(String threadId, String question) {
        log.info("[DeerFlow] askQuestion 进入，threadId={}, question={}", threadId, question);
        return getValidCredential().flatMap(cred -> {
            ThreadRunStreamReq req = new ThreadRunStreamReq();
            ThreadRunStreamReq.InputDTO input = new ThreadRunStreamReq.InputDTO();
            ThreadRunStreamReq.HumanMessageDTO humanMsg = new ThreadRunStreamReq.HumanMessageDTO();
            humanMsg.setId("local-human-" + UUID.randomUUID());
            ThreadRunStreamReq.ContentItemDTO contentItem = new ThreadRunStreamReq.ContentItemDTO();
            contentItem.setText(question);
            // JDK8 没有List.of 使用Arrays.asList
            humanMsg.setContent(Arrays.asList(contentItem));
            input.setMessages(Arrays.asList(humanMsg));
            req.setInput(input);
            ThreadRunStreamReq.ConfigDTO config = new ThreadRunStreamReq.ConfigDTO();
            config.setRecursionLimit(1000);
            req.setConfig(config);

            log.info("[DeerFlow] 准备调用SSE接口 /api/threads/{threadId}/runs/stream threadId={}, X‑CSRF‑Token={}",
                    threadId, cred.getCsrfToken());
            Flux<String> sseFlux = deerFlowWebClient.post()
                    .uri("/api/threads/{threadId}/runs/stream", threadId)
                    .header("X-CSRF-Token", cred.getCsrfToken())
                    .header(HttpHeaders.COOKIE, cred.getCookieHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(req)
                    .retrieve()
                    .onStatus(status -> status.value() == 403, resp -> {
                        log.info("[DeerFlow] askQuestion SSE请求收到403，清空凭证缓存");
                        authCredentialCache.set(null);
                        return Mono.error(new RuntimeException("DeerFlow 403，CSRF凭证失效，已清空缓存"));
                    })
                    .bodyToFlux(String.class);

            return sseFlux
                    .doOnNext(line -> {
                        if (line.startsWith("data:")) {
                            String jsonStr = line.substring("data:".length()).trim();
                            log.info("[DeerFlow] SSE data事件:{}", jsonStr);
                        }
                    })
                    .doOnComplete(() -> log.info("[DeerFlow] SSE流接收完成，准备调用fetchAiAnswerAfterStream获取结果, threadId={}", threadId))
                    .then(fetchAiAnswerAfterStream(threadId));
        });
    }

    public Mono<String> fetchAiAnswerAfterStream(String threadId) {
        log.info("[DeerFlow] fetchAiAnswerAfterStream 进入，threadId={}", threadId);
        return getValidCredential().flatMap(cred -> {
            log.info("[DeerFlow] 请求消息列表接口 /api/threads/{threadId}/messages threadId={}", threadId);
            return deerFlowWebClient.get()
                    .uri("/api/threads/{threadId}/messages", threadId)
                    .header("X-CSRF-Token", cred.getCsrfToken())
                    .header(HttpHeaders.COOKIE, cred.getCookieHeader())
                    .retrieve()
                    .onStatus(status -> status.value() == 403, resp -> {
                        log.info("[DeerFlow] fetchAiAnswerAfterStream收到403，清空凭证缓存");
                        authCredentialCache.set(null);
                        return Mono.error(new RuntimeException("DeerFlow 403，CSRF凭证失效，已清空缓存"));
                    })
                    .bodyToMono(String.class)
                    .map(jsonStr -> {
                        log.info("[DeerFlow] messages接口返回json长度={}", jsonStr.length());
                        try {
                            JsonNode root = objectMapper.readTree(jsonStr);
                            // 倒序遍历，拿最新消息
                            for (int i = root.size() - 1; i >= 0; i--) {
                                JsonNode msg = root.get(i);
                                String eventType = msg.get("event_type").asText();
                                log.info("[DeerFlow] 遍历消息索引={}, event_type={}", i, eventType);
                                // AI回复的标记
                                if ("llm.ai.response".equals(eventType)) {
                                    JsonNode contentObj = msg.get("content");
                                    JsonNode sb = contentObj.get("content");
                                    String result = sb.toString();
                                    log.info("[DeerFlow] 匹配到llm.ai.response，返回结果长度={}, result={}", result.length(), result);
                                    return result;
                                }
                            }
                            log.info("[DeerFlow] 遍历全部消息，未找到llm.ai.response事件");
                            return "未找到AI回复";
                        } catch (Exception e) {
                            log.error("[DeerFlow] 解析消息异常", e);
                            return "解析失败";
                        }
                    });
        });
    }
}
