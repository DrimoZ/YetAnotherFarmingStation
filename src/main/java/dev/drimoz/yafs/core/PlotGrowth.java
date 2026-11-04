package dev.drimoz.yafs.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlotGrowth(float progress, int density) {

    public static final PlotGrowth SOWN = new PlotGrowth(0F, 1);

    public static final Codec<PlotGrowth> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0F, 1F).fieldOf("progress").forGetter(PlotGrowth::progress),
            Codec.intRange(1, 64).optionalFieldOf("density", 1).forGetter(PlotGrowth::density)
    ).apply(instance, PlotGrowth::new));

    public PlotGrowth {
        if (progress < 0F || progress > 1F || density < 1) {
            throw new IllegalArgumentException("Croissance invalide : " + progress + " × " + density);
        }
    }

    public boolean isMature() {
        return this.progress >= 1F;
    }

    public PlotGrowth advance(double fraction) {
        return new PlotGrowth((float) Math.min(1D, this.progress + Math.max(0D, fraction)), this.density);
    }

    public PlotGrowth harvested() {
        return new PlotGrowth(0F, this.density);
    }
}
