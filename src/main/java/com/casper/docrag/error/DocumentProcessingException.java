package com.casper.docrag.error;

/** 文件處理管線任一步失敗（解析/分塊/embedding/儲存）。錯誤透明（SPEC §7）。 */
public class DocumentProcessingException extends RuntimeException {

    public DocumentProcessingException(String message) {
        super(message);
    }

    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
