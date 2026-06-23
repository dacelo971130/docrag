package com.casper.docrag.web.dto;

import java.time.Instant;

/** 統一錯誤回應。 */
public record ErrorResponse(int status, String error, String message, Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}
