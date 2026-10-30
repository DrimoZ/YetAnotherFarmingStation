package dev.drimoz.yafs.content.greenhouse;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Ce que laisse passer un bloc de serre (YAF-050, YAF-051). {@link #NONE} : pas de port, le bloc se
 * comporte en socle ou en mur selon sa couche. Un port n'a qu'un mode ; les sorties d'items portent
 * leur filtre dans le mode. Pas d'entrée d'items : rien n'entre en continu tant qu'il n'y a pas
 * d'engrais (YAF-022).
 */
public enum PortMode implements StringRepresentable {
    NONE,
    OUTPUT_ALL,
    /** Tout ce qui n'est pas une graine de culture connue. */
    OUTPUT_HARVEST,
    /** Les graines en trop : ce que {@code Crops} reconnaît comme culture. */
    OUTPUT_SEEDS,
    FLUID_INPUT,
    ENERGY_INPUT;

    public boolean isPort() {
        return this != NONE;
    }

    public boolean isOutput() {
        return this == OUTPUT_ALL || this == OUTPUT_HARVEST || this == OUTPUT_SEEDS;
    }

    /** Le mode suivant, en tournant ; un mur ne se tourne pas en port par là. */
    public PortMode next() {
        PortMode[] modes = values();
        return modes[ordinal() + 1 < modes.length ? ordinal() + 1 : 1];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
