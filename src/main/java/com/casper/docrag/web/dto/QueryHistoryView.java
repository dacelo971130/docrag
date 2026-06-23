package com.casper.docrag.web.dto;

import java.time.Instant;

/** GET /api/query/history 列表項（SPEC §3.2）。 */
public record QueryHistoryView(String question, String answer, Instant timestamp) {
}
