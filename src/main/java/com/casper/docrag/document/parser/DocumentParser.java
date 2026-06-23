package com.casper.docrag.document.parser;

/** 文件解析抽象（SPEC §4.1 步驟 1）。可依型別擴充更多 parser。 */
public interface DocumentParser {

    /** 是否能處理此 content type / 副檔名。 */
    boolean supports(String contentType, String filename);

    /** 抽取純文字。 */
    String parse(byte[] content);
}
