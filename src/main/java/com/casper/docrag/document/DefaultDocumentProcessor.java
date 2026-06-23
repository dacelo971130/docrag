package com.casper.docrag.document;

import com.casper.docrag.document.chunk.Chunker;
import com.casper.docrag.document.parser.DocumentParser;
import com.casper.docrag.document.parser.DocumentParserResolver;
import com.casper.docrag.domain.DocumentProcessor;
import com.casper.docrag.domain.EmbeddingProvider;
import com.casper.docrag.domain.VectorStore;
import com.casper.docrag.domain.model.DocumentChunk;
import com.casper.docrag.domain.model.ProcessingResult;
import com.casper.docrag.domain.model.UploadedDocument;
import com.casper.docrag.error.DocumentProcessingException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件處理管線實作（SPEC §4.1）：解析 → 分塊 → embedding（批次）→ 儲存。
 *
 * <p>純編排、無 DB 狀態副作用，外部相依（parser/chunker/embedding/vectorStore）皆以介面注入，
 * 可獨立 mock 測試（SPEC §6/§7）。狀態轉移（PROCESSING/READY/FAILED）由 DocumentService 負責。
 */
@Component
public class DefaultDocumentProcessor implements DocumentProcessor {

    private final DocumentParserResolver parserResolver;
    private final Chunker chunker;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;

    public DefaultDocumentProcessor(DocumentParserResolver parserResolver,
                                    Chunker chunker,
                                    EmbeddingProvider embeddingProvider,
                                    VectorStore vectorStore) {
        this.parserResolver = parserResolver;
        this.chunker = chunker;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
    }

    @Override
    public ProcessingResult process(UploadedDocument doc) {
        // 1. 解析
        DocumentParser parser = parserResolver.resolve(doc.contentType(), doc.name());
        String text = parser.parse(doc.content());
        if (text == null || text.isBlank()) {
            throw new DocumentProcessingException("文件無可擷取的文字內容");
        }

        // 2. 分塊
        List<String> pieces = chunker.chunk(text);
        if (pieces.isEmpty()) {
            throw new DocumentProcessingException("分塊結果為空");
        }

        // 3. Embedding（批次，成本意識 §7）
        List<float[]> embeddings = embeddingProvider.embedBatch(pieces);
        if (embeddings.size() != pieces.size()) {
            throw new DocumentProcessingException(
                    "embedding 數量(" + embeddings.size() + ")與片段數(" + pieces.size() + ")不符");
        }

        // 4. 儲存
        List<DocumentChunk> chunks = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            chunks.add(new DocumentChunk(UUID.randomUUID(), doc.id(), i, pieces.get(i), embeddings.get(i)));
        }
        vectorStore.upsert(chunks);

        return ProcessingResult.ready(doc.id(), chunks.size());
    }
}
