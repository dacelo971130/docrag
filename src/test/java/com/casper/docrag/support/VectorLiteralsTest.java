package com.casper.docrag.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorLiteralsTest {

    @Test
    void formatsAsPgvectorLiteral() {
        assertThat(VectorLiterals.toLiteral(new float[]{0.1f, 0.2f, -0.3f}))
                .isEqualTo("[0.1,0.2,-0.3]");
    }

    @Test
    void handlesSingleElement() {
        assertThat(VectorLiterals.toLiteral(new float[]{1.0f})).isEqualTo("[1.0]");
    }
}
