package dev.drimoz.yafs;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class YafsConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue ENERGIZED_SPEED = BUILDER
            .comment("Vitesse de croissance d'une parcelle alimentée en énergie, par rapport à une parcelle sans énergie.",
                    "Growth speed of a powered plot, relative to an unpowered one.")
            .defineInRange("energizedSpeed", 2.0D, 1.0D, 16.0D);

    public static final ModConfigSpec.DoubleValue GROWTH_SPEED = BUILDER
            .comment("Multiplicateur de la vitesse de toute croissance, pour équilibrer un modpack d'un coup ; 1 : sans effet.",
                    "Multiplier on every growth, to balance a whole modpack at once; 1: no effect.")
            .defineInRange("growthSpeed", 1.0D, 0.05D, 100.0D);

    public static final ModConfigSpec.BooleanValue RESIDUES = BUILDER
            .comment("Les récoltes laissent-elles des résidus (du compost) ? Faux : aucun, nulle part.",
                    "Do harvests leave residues (compost)? False: none, anywhere.")
            .define("residues", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private YafsConfig() {}
}
