package dev.drimoz.yafs.content.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Ce que fait un module de serre (YAF-060), défini en datapack : {@code data/<ns>/yafs/module/*.json},
 * du même identifiant que l'item {@link ModuleItem} qu'il décrit. Un datapack retouche un module
 * existant ; une définition sans item ne fait rien.
 * Registre de datapack, synchronisé par le jeu ; il se recharge au lancement du monde, pas au
 * {@code /reload}, ce qui suffit pour un contenu qu'on ne retouche pas en jouant.
 *
 * <pre>{@code
 * { "temperature_shift": 20, "energy_per_tick": 40 }
 * }</pre>
 *
 * @param lightFloor        la lumière ne descend pas sous ce niveau tant que le module tourne
 * @param temperatureShift  degrés ajoutés à la température du lieu (négatif pour refroidir)
 * @param rainCollection    mB recueillis par seconde quand il pleut sur la serre
 * @param energyPerTick     FE consommés par tick ; sans énergie, le module ne fait rien
 * @param speedMultiplier   vitesse d'une parcelle alimentée, multipliée ; sans énergie, rien
 * @param energyMultiplier  coût en énergie de chaque parcelle alimentée, multiplié
 * @param waterMultiplier   eau bue par une parcelle alimentée, multipliée
 * @param density           plants par parcelle ; le plus haut des modules, borné par le palier
 * @param maxStack          combien de ce module une case accepte ; leurs effets s'additionnent
 */
public record ModuleDefinition(int lightFloor, double temperatureShift, int rainCollection, int energyPerTick,
                               double speedMultiplier, double energyMultiplier, double waterMultiplier, int density, int maxStack) {

    /**
     * Un multiplicateur empilé {@code count} fois : les bonus s'additionnent. Deux Accélérateurs I
     * (×2, soit +100 % chacun) font ×3, pas ×4. Des modules différents, eux, se multiplient.
     */
    public static double stacked(double multiplier, int count) {
        return 1D + (multiplier - 1D) * count;
    }

    public static final ResourceKey<Registry<ModuleDefinition>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "module"));

    public static final Codec<ModuleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 15).optionalFieldOf("light_floor", 0).forGetter(ModuleDefinition::lightFloor),
            Codec.doubleRange(-60D, 60D).optionalFieldOf("temperature_shift", 0D).forGetter(ModuleDefinition::temperatureShift),
            Codec.intRange(0, 1_000).optionalFieldOf("rain_collection", 0).forGetter(ModuleDefinition::rainCollection),
            Codec.intRange(0, 10_000).optionalFieldOf("energy_per_tick", 0).forGetter(ModuleDefinition::energyPerTick),
            Codec.doubleRange(1D, 64D).optionalFieldOf("speed_multiplier", 1D).forGetter(ModuleDefinition::speedMultiplier),
            Codec.doubleRange(1D, 1_000D).optionalFieldOf("energy_multiplier", 1D).forGetter(ModuleDefinition::energyMultiplier),
            Codec.doubleRange(1D, 64D).optionalFieldOf("water_multiplier", 1D).forGetter(ModuleDefinition::waterMultiplier),
            Codec.intRange(1, 16).optionalFieldOf("density", 1).forGetter(ModuleDefinition::density),
            Codec.intRange(1, 64).optionalFieldOf("max_stack", 1).forGetter(ModuleDefinition::maxStack)
    ).apply(instance, ModuleDefinition::new));
}
