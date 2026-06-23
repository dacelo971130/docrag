package com.casper.docrag.query;

import com.casper.docrag.domain.model.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void buildsTemplateWithNumberedChunksAndQuestion() {
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), 0, "第一段內容", 0.91),
                new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), 1, "第二段內容", 0.82));

        String prompt = builder.build("這是什麼？", chunks);

        assertThat(prompt)
                .contains("[1] 第一段內容")
                .contains("[2] 第二段內容")
                .contains("這是什麼？")
                .contains("【文件片段】")
                .contains("【問題】")
                // 防線 3：抑制幻覺的指示語
                .contains("文件中找不到相關資訊");
    }

    @Test
    void handlesSingleChunk() {
        String prompt = builder.build("Q",
                List.of(new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), 0, "only", 0.9)));
        assertThat(prompt).contains("[1] only");
    }
}
