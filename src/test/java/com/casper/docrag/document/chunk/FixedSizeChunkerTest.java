package com.casper.docrag.document.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedSizeChunkerTest {

    @Test
    void nullOrBlankYieldsNoChunks() {
        FixedSizeChunker chunker = new FixedSizeChunker(10, 2);
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   \n  ")).isEmpty();
    }

    @Test
    void shortTextIsOneChunk() {
        FixedSizeChunker chunker = new FixedSizeChunker(100, 10);
        assertThat(chunker.chunk("hello world")).containsExactly("hello world");
    }

    @Test
    void splitsLatinWordsWithOverlap() {
        // 10 word-tokens, chunkSize=4, overlap=1 → step=3
        FixedSizeChunker chunker = new FixedSizeChunker(4, 1);
        List<String> chunks = chunker.chunk("a b c d e f g h i j");
        assertThat(chunks).containsExactly("a b c d", "d e f g", "g h i j");
    }

    @Test
    void countsEachCjkCharacterAsOneToken() {
        FixedSizeChunker chunker = new FixedSizeChunker(2, 0);
        assertThat(chunker.chunk("中文測試")).containsExactly("中文", "測試");
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new FixedSizeChunker(4, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedSizeChunker(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedSizeChunker(4, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
