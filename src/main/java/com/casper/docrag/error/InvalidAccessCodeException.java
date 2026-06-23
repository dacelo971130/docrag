package com.casper.docrag.error;

/** 缺少或錯誤的存取碼（X-Access-Code）→ HTTP 401（ADR-009 存取碼防濫用）。 */
public class InvalidAccessCodeException extends RuntimeException {

    public InvalidAccessCodeException(String message) {
        super(message);
    }
}
