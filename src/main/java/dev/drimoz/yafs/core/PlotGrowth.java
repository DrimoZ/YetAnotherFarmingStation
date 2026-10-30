package dev.drimoz.yafs.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * L'état de croissance d'une parcelle : où elle en est, et combien de plants elle représente.
 *
 * <p>Ce que contient la parcelle (culture, substrat, catalyseur) est un inventaire de la serre,
 * pas une donnée d'ici : {@code core/} ne connaît pas les items.
 *
 * @param progress de 0 (semée) à 1 (mûre)
 * @param density  le nombre de plants que la parcelle représente, de 1 au plafond du palier
 */
public record PlotGrowth(float progress, int density) {

    public static final PlotGrowth SOWN = new PlotGrowth(0F, 1);

    /** Borné : une sauvegarde corrompue est refusée, pas ramenée en silence dans les clous. */
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

    /** Avance d'une fraction de la croissance totale, plafonnée à mûre. */
    public PlotGrowth advance(double fraction) {
        return new PlotGrowth((float) Math.min(1D, this.progress + Math.max(0D, fraction)), this.density);
    }

    /** Récoltée : on repart de zéro, à densité égale. */
    public PlotGrowth harvested() {
        return new PlotGrowth(0F, this.density);
    }
}
