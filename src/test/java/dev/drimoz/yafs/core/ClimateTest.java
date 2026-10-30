package dev.drimoz.yafs.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClimateTest {

    private static final double EPSILON = 1e-4;

    @Test
    void theAnchorsHold() {
        assertEquals(20D, Climate.celsius(0.8F), EPSILON);
        assertEquals(40D, Climate.celsius(2.0F), EPSILON);
    }

    @Test
    void frozenBiomesAreBelowZeroAndTaigaIsCool() {
        assertTrue(Climate.celsius(-0.5F) < 0D);
        assertEquals(10.83D, Climate.celsius(0.25F), 0.01D);
    }
}
