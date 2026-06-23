package com.casper.docrag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 背景處理與 SSE 串流用的執行緒池，彼此隔離以免互相飢餓。 */
@Configuration
public class AsyncConfig {

    /** 文件處理（解析/embedding，較長且 IO 密集）。 */
    @Bean("documentProcessingExecutor")
    public Executor documentProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-proc-");
        executor.initialize();
        return executor;
    }

    /** 查詢串流（驅動 LLM token → SseEmitter）。 */
    @Bean("queryStreamExecutor")
    public Executor queryStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("query-sse-");
        executor.initialize();
        return executor;
    }
}
