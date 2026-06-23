package com.casper.docrag.document.chunk;

import com.casper.docrag.config.AppProperties;
import com.casper.docrag.support.CjkText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小分塊 + overlap（ADR-004）。
 *
 * <p>以「近似 token」為單位切分：每個 CJK 字視為 1 個 token，連續的拉丁字母/數字視為 1 個
 * token，其餘非空白字元各 1 個 token。如此可在不引入 BPE tokenizer 依賴下，對中英文皆給出
 * 穩定、可測試的固定大小切分；token 精確化（改用模型 tokenizer）留待 M3。
 *
 * <p>切分以原文字元位移重建，保留片段內原始空白與標點。
 */
@Component
public class FixedSizeChunker implements Chunker {

    private final int chunkSize;
    private final int overlap;

    @Autowired
    public FixedSizeChunker(AppProperties props) {
        this(props.chunking().chunkSize(), props.chunking().overlap());
    }

    public FixedSizeChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必須 > 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap 必須介於 [0, chunkSize)");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<int[]> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        for (int start = 0; start < tokens.size(); start += step) {
            int end = Math.min(start + chunkSize, tokens.size());
            int from = tokens.get(start)[0];
            int to = tokens.get(end - 1)[1];
            String piece = text.substring(from, to).strip();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end == tokens.size()) {
                break;
            }
        }
        return chunks;
    }

    /** 回傳每個 token 在原文中的 [start, end) 位移。 */
    static List<int[]> tokenize(String s) {
        List<int[]> spans = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (isCjk(c)) {
                spans.add(new int[]{i, i + 1});
                i++;
            } else if (isWordChar(c)) {
                int j = i + 1;
                while (j < n && isWordChar(s.charAt(j))) {
                    j++;
                }
                spans.add(new int[]{i, j});
                i = j;
            } else {
                spans.add(new int[]{i, i + 1});
                i++;
            }
        }
        return spans;
    }

    private static boolean isWordChar(char c) {
        return (Character.isLetterOrDigit(c) || c == '_') && !isCjk(c);
    }

    private static boolean isCjk(char c) {
        return CjkText.isCjk(c);
    }
}
