package com.casper.docrag.domain;

import com.casper.docrag.domain.model.ProcessingResult;
import com.casper.docrag.domain.model.UploadedDocument;

/**
 * 文件處理管線（SPEC §3.1, §4.1）：解析 → 分塊 → embedding → 儲存。
 */
public interface DocumentProcessor {

    ProcessingResult process(UploadedDocument doc);
}
