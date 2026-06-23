package com.casper.docrag.document.chunk;

import java.util.List;

/**
 * 分塊策略抽象（SPEC §4.1，ADR-004）。預設固定大小 + overlap；
 * 介面化以利後續實驗語意分塊（M3）而不動上層。
 */
public interface Chunker {

    List<String> chunk(String text);
}
