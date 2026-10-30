package dev.drimoz.yafs.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotGrowthTest {

    @Test
    void theCodecRoundTrips() {
        PlotGrowth growth = new PlotGrowth(0.25F, 3);

        JsonElement json = PlotGrowth.CODEC.encodeStart(JsonOps.INSTANCE, growth).getOrThrow();

        assertEquals(growth, PlotGrowth.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void anOutOfRangeSaveIsRefusedNotClamped() {
        JsonElement json = JsonParser.parseString("{\"progress\": 1.5}");

        assertTrue(PlotGrowth.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }

    @Test
    void aMissingDensityMeansOnePlant() {
        JsonElement json = JsonParser.parseString("{\"progress\": 0.5}");

        assertEquals(1, PlotGrowth.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().density());
    }

    @Test
    void growthStopsAtMatureAndRestartsOnHarvest() {
        PlotGrowth growth = PlotGrowth.SOWN.advance(0.6).advance(0.6);

        assertTrue(growth.isMature());
        assertEquals(1F, growth.progress());
        assertFalse(growth.harvested().isMature());
        assertEquals(0F, growth.harvested().progress());
    }
}
