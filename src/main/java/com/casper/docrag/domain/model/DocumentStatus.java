package com.casper.docrag.domain.model;

/** 文件處理生命週期狀態（SPEC §3.3 documents.status）。 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}
