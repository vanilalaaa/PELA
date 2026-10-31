package com.pela.shared.domain.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Random;
import org.junit.jupiter.api.Test;

class VectorMathTest {

    @Test
    void normalizedVectorHasUnitLengthAndSameDirection() {
        float[] normalized = VectorMath.l2Normalize(new float[] {3f, 4f});

        assertThat(normalized[0]).isCloseTo(0.6f, within(1e-6f));
        assertThat(normalized[1]).isCloseTo(0.8f, within(1e-6f));
        assertThat(VectorMath.l2Norm(normalized)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void works768Dimensions() {
        Random random = new Random(42);
        float[] vector = new float[768];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (random.nextGaussian() * 0.05);
        }

        float[] normalized = VectorMath.l2Normalize(vector);

        assertThat(normalized).hasSize(768);
        assertThat(VectorMath.l2Norm(normalized)).isCloseTo(1.0, within(1e-5));
        assertThat(VectorMath.l2Norm(VectorMath.l2Normalize(normalized))).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void rejectsZeroAndNonFiniteVectors() {
        assertThatThrownBy(() -> VectorMath.l2Normalize(new float[] {0f, 0f})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VectorMath.l2Normalize(new float[] {1f, Float.NaN})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VectorMath.l2Normalize(new float[] {Float.POSITIVE_INFINITY})).isInstanceOf(IllegalArgumentException.class);
    }
}
