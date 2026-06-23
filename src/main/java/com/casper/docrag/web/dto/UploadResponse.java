package com.casper.docrag.web.dto;

import java.util.UUID;

/** POST /api/documents 回應（SPEC §3.2）。 */
public record UploadResponse(UUID documentId, String status) {
}
