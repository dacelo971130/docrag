package com.casper.docrag.error;

/** 不支援的文件型別（非 PDF / Markdown）→ HTTP 415。 */
public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String message) {
        super(message);
    }
}
