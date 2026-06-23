package com.casper.docrag.document.parser;

import com.casper.docrag.error.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** PDF 解析（Apache PDFBox 3.x）。 */
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType, String filename) {
        if (contentType != null && contentType.toLowerCase().contains("pdf")) {
            return true;
        }
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public String parse(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException e) {
            throw new DocumentProcessingException("PDF 解析失敗：" + e.getMessage(), e);
        }
    }
}
