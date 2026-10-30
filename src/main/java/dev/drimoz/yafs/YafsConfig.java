package dev.drimoz.yafs;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Réglages serveur : ce qu'un modpack équilibre sans toucher aux données. */
public final class YafsConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue ENERGIZED_SPEED = BUILDER
            .comment("Vitesse de croissance d'une parcelle alimentée en énergie, par rapport à une parcelle sans énergie.",
                    "Growth speed of a powered plot, relative to an unpowered one.")
            .defineInRange("energizedSpeed", 2.0D, 1.0D, 16.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private YafsConfig() {}
}
