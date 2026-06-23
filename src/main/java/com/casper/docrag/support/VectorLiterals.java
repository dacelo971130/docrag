package com.casper.docrag.support;

/** float[] ↔ pgvector 文字字面值（{@code [v1,v2,...]}）轉換。配合 SQL 的 {@code ::vector} 轉型使用。 */
public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 9 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
