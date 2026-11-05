package com.changgeng.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class DeerFlowConfig {

    @Value("${deerflow.url:http://localhost:8001}")
    private String deerflowUrl;

    @Bean
    public WebClient deerFlowWebClient() {
        HttpClient httpClient = HttpClient.create()
                // 连接超时：10秒
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100000)
                // 读取超时：120秒，SSE长时间无数据包就会触发这个异常
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(300, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", "PostmanRuntime/7.43.0")
                .baseUrl(deerflowUrl)
                .build();
    }
}
