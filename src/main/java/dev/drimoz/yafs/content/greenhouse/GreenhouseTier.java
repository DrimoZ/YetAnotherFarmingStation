package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.Codec;
import dev.drimoz.yafs.content.multiblock.MultiblockShape;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Les paliers d'une serre, voir {@code docs/01-DESIGN.md} §2.2. Un palier donne de la place
 * (parcelles, modules, ports, densité), jamais de vitesse.
 *
 * @param plotSide côté de la grille de parcelles : 2 pour 2×2
 */
public enum GreenhouseTier implements StringRepresentable {
    I(new MultiblockShape(3, 3, 3), 2, 2, 2, 2),
    II(new MultiblockShape(5, 4, 5), 3, 4, 4, 3),
    III(new MultiblockShape(7, 5, 7), 4, 6, 6, 4);

    public static final Codec<GreenhouseTier> CODEC = StringRepresentable.fromEnum(GreenhouseTier::values);

    private final MultiblockShape shape;
    private final int plotSide;
    private final int moduleSlots;
    private final int maxPorts;
    private final int maxDensity;

    GreenhouseTier(MultiblockShape shape, int plotSide, int moduleSlots, int maxPorts, int maxDensity) {
        this.shape = shape;
        this.plotSide = plotSide;
        this.moduleSlots = moduleSlots;
        this.maxPorts = maxPorts;
        this.maxDensity = maxDensity;
    }

    public MultiblockShape shape() {
        return this.shape;
    }

    public int plotSide() {
        return this.plotSide;
    }

    public int plots() {
        return this.plotSide * this.plotSide;
    }

    public int moduleSlots() {
        return this.moduleSlots;
    }

    public int maxPorts() {
        return this.maxPorts;
    }

    public int maxDensity() {
        return this.maxDensity;
    }

    /** Le palier qu'on peut poser par-dessus celui-ci ; {@code null} au dernier. */
    @Nullable
    public GreenhouseTier next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
