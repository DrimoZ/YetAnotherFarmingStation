package dev.drimoz.yafs.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotStatusTest {

    @Test
    void theGreenhouseTakesItsWorstPlot() {
        assertEquals(PlotStatus.PROBLEM, PlotStatus.worst(List.of(PlotStatus.WORKING, PlotStatus.PROBLEM, PlotStatus.BLOCKED)));
        assertEquals(PlotStatus.BLOCKED, PlotStatus.worst(List.of(PlotStatus.WORKING, PlotStatus.BLOCKED, PlotStatus.WAITING)));
    }

    @Test
    void oneGrowingPlotOutranksEmptyOnes() {
        assertEquals(PlotStatus.WORKING, PlotStatus.worst(List.of(PlotStatus.WAITING, PlotStatus.WORKING, PlotStatus.WAITING)));
    }

    @Test
    void aSwitchedOffGreenhouseIsOff() {
        assertEquals(PlotStatus.OFF, PlotStatus.worst(List.of(PlotStatus.OFF, PlotStatus.OFF)));
        assertEquals(PlotStatus.OFF, PlotStatus.worst(List.of()));
    }
}
