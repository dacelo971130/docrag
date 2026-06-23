package com.casper.docrag.domain.model;

import java.util.UUID;

/**
 * 文件處理管線的輸入（SPEC §4.1）。在 metadata 列已寫入 documents 後建立，
 * 攜帶原始位元組供解析使用。
 */
public record UploadedDocument(
        UUID id,
        String name,
        String contentType,
        byte[] content
) {
}
