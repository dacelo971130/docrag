package com.casper.docrag.document.parser;

import com.casper.docrag.error.UnsupportedDocumentTypeException;
import org.springframework.stereotype.Component;

import java.util.List;

/** 依 content type / 副檔名挑選合適的 {@link DocumentParser}。 */
@Component
public class DocumentParserResolver {

    private final List<DocumentParser> parsers;

    public DocumentParserResolver(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser resolve(String contentType, String filename) {
        return parsers.stream()
                .filter(p -> p.supports(contentType, filename))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentTypeException(
                        "不支援的文件型別：contentType=" + contentType + ", filename=" + filename
                                + "。目前支援 PDF 與 Markdown/純文字。"));
    }
}
