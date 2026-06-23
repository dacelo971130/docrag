package com.casper.docrag.web;

import com.casper.docrag.config.AppProperties;
import com.casper.docrag.query.QueryService;
import com.casper.docrag.web.dto.QueryHistoryView;
import com.casper.docrag.web.dto.QueryRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 查詢 REST 端點（SPEC §3.2）。串流採 MVC + SseEmitter（ADR-006）；限流由攔截器前置（§4.2 步驟 0）。
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);
    private static final long STREAM_TIMEOUT_MS = 120_000L;

    private final QueryService queryService;
    private final AppProperties props;
    private final Executor streamExecutor;

    public QueryController(QueryService queryService,
                           AppProperties props,
                           @Qualifier("queryStreamExecutor") Executor streamExecutor) {
        this.queryService = queryService;
        this.props = props;
        this.streamExecutor = streamExecutor;
    }

    /** 串流問答。逐 token 以 SSE data 事件送出，結束送 name=done 的 [DONE]。 */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody QueryRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streamExecutor.execute(() -> drive(emitter, request));
        return emitter;
    }

    private void drive(SseEmitter emitter, QueryRequest request) {
        try {
            queryService.streamAnswer(request.question(), request.documentScope(), token -> send(emitter, token));
            done(emitter);
        } catch (Exception e) {
            log.warn("查詢串流失敗，回降級訊息：{}", e.getMessage());
            try {
                send(emitter, props.llm().fallbackMessage());
                done(emitter);
            } catch (RuntimeException ignored) {
                emitter.completeWithError(e);
            }
        }
    }

    private static void send(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void done(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /** 查詢歷史（最新在前）。 */
    @GetMapping("/history")
    public List<QueryHistoryView> history(@RequestParam(defaultValue = "20") int limit) {
        int bounded = Math.min(Math.max(limit, 1), 100);
        return queryService.history(bounded).stream()
                .map(r -> new QueryHistoryView(r.question(), r.answer(), r.createdAt()))
                .toList();
    }
}
