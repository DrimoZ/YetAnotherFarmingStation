package dev.drimoz.yafs.core;

import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.core.Efficiency.Factor;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import dev.drimoz.yafs.core.Efficiency.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EfficiencyTest {

    private static final Requirements WHEAT = new Requirements(9, 10D, 30D);
    private static final double EPSILON = 1e-9;

    @Test
    void idealConditionsGiveFullSpeed() {
        Verdict verdict = Efficiency.evaluate(WHEAT, new Conditions(15, 20D, 1D));

        assertTrue(verdict.isIdeal());
        assertEquals(1D, verdict.value(), EPSILON);
    }

    @Test
    void onlyTheWorstFactorCounts() {
        // Lumière un peu basse (-2 niveaux) et froid marqué (-6 °C) : la température l'emporte.
        Verdict verdict = Efficiency.evaluate(WHEAT, new Conditions(7, 4D, 1D));

        assertEquals(Factor.TEMPERATURE, verdict.limiting());
        assertEquals(1D - 6D / Efficiency.TEMPERATURE_SPAN, verdict.value(), EPSILON);
    }

    @Test
    void eachFactorCanBeTheLimitingOne() {
        assertEquals(Factor.LIGHT, Efficiency.evaluate(WHEAT, new Conditions(5, 20D, 1D)).limiting());
        assertEquals(Factor.WATER, Efficiency.evaluate(WHEAT, new Conditions(15, 20D, 0.5D)).limiting());
        assertEquals(Factor.TEMPERATURE, Efficiency.evaluate(WHEAT, new Conditions(15, 40D, 1D)).limiting());
    }

    @Test
    void aGapSlowsDownButNeverStops() {
        Verdict verdict = Efficiency.evaluate(WHEAT, new Conditions(0, -40D, 1D));

        assertEquals(Efficiency.FLOOR, verdict.value(), EPSILON);
    }

    @Test
    void anEmptyReserveStopsGrowth() {
        Verdict verdict = Efficiency.evaluate(WHEAT, new Conditions(15, 20D, 0D));

        assertEquals(Factor.WATER, verdict.limiting());
        assertEquals(0D, verdict.value(), EPSILON);
    }
}
