package com.pela.shared.domain.vector;

/** Phép toán vector cho embedding (cosine similarity trên pgvector cần vector đã chuẩn hoá). */
public final class VectorMath {

    private VectorMath() {
    }

    /** Chia vector cho độ dài L2 của nó. Vector toàn 0 hoặc chứa NaN/vô cực là lỗi. */
    public static float[] l2Normalize(float[] vector) {
        double sumSquares = 0;
        for (float v : vector) {
            if (!Float.isFinite(v)) {
                throw new IllegalArgumentException("Vector contains a non-finite value");
            }
            sumSquares += (double) v * v;
        }
        double norm = Math.sqrt(sumSquares);
        if (norm == 0) {
            throw new IllegalArgumentException("Cannot normalize a zero vector");
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    public static double l2Norm(float[] vector) {
        double sumSquares = 0;
        for (float v : vector) {
            sumSquares += (double) v * v;
        }
        return Math.sqrt(sumSquares);
    }
}
