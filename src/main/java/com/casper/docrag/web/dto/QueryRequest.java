package com.casper.docrag.web.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/query/stream 請求（SPEC §3.2）。documentScope 選填，限定檢索範圍。 */
public record QueryRequest(
        @NotBlank(message = "question 不可為空") String question,
        String documentScope
) {
}
