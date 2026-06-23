package com.casper.docrag.document.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Markdown / 純文字解析（直接讀取，SPEC §4.1）。 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType, String filename) {
        if (filename != null) {
            String f = filename.toLowerCase();
            if (f.endsWith(".md") || f.endsWith(".markdown") || f.endsWith(".txt")) {
                return true;
            }
        }
        if (contentType != null) {
            String c = contentType.toLowerCase();
            return c.contains("markdown") || c.startsWith("text/");
        }
        return false;
    }

    @Override
    public String parse(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }
}
