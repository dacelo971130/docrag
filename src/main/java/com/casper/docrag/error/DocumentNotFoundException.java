package com.casper.docrag.error;

import java.util.UUID;

/** 找不到指定文件 → HTTP 404。 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("找不到文件：" + id);
    }
}
