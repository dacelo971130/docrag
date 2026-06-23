package com.casper.docrag.query;

import com.casper.docrag.domain.model.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 組裝（SPEC §4.3，防線 3）。指示模型僅依檢索片段作答、資訊不足即明說找不到，
 * 抑制幻覺。片段以 [1][2]… 編號，供後處理引用驗證（防線 4）對照。
 */
@Component
public class PromptBuilder {

    private static final String TEMPLATE = """
            你是一個文件問答助手。僅根據以下提供的文件片段回答問題。
            若片段中沒有足夠資訊，明確說明「文件中找不到相關資訊」，不要編造。
            引用片段時可標註其編號（如 [1]、[2]）。

            【文件片段】
            %s

            【問題】
            %s

            【回答】
            """;

    public String build(String question, List<RetrievedChunk> chunks) {
        return TEMPLATE.formatted(formatChunks(chunks), question == null ? "" : question.strip());
    }

    private String formatChunks(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append('[').append(i + 1).append("] ").append(chunks.get(i).content().strip());
        }
        return sb.toString();
    }
}
