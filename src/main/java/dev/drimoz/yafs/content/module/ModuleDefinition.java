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
 * @param density           plants par parcelle quand il est seul ; chaque module ajoute {@code density - 1} plants
 * @param maxStack          combien de ce module une serre accepte ; leurs effets s'additionnent
 * @param energyCapacity    FE de réserve en plus, par module

 * @param lightCap          la lumière ne monte pas au-dessus de ce niveau tant que le module tourne (15 : sans effet)
 * @param outputCapacity   items en plus que chaque case de sortie accepte, par module
 */
public record ModuleDefinition(int lightFloor, double temperatureShift, int rainCollection, int energyPerTick,
                               double speedMultiplier, double energyMultiplier, double waterMultiplier, int density, int maxStack,
                               int energyCapacity, int lightCap, int outputCapacity) {

    /** Une serre est éclairée ou plongée dans le noir, pas les deux ; et deux lumières, seule la plus forte compterait. */
    public boolean excludes(ModuleDefinition other) {
        boolean lights = this.lightFloor > 0 && other.lightFloor > 0;
        boolean darks = this.lightCap < 15 && other.lightCap < 15;
        boolean opposed = (this.lightFloor > 0 && other.lightCap < 15) || (this.lightCap < 15 && other.lightFloor > 0);
        return lights || darks || opposed;
    }

    /** Les plants que ce module ajoute à chaque parcelle. */
    public int extraPlants() {
        return this.density - 1;
    }

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
            Codec.intRange(1, 64).optionalFieldOf("max_stack", 16).forGetter(ModuleDefinition::maxStack),
            Codec.intRange(0, 100_000_000).optionalFieldOf("energy_capacity", 0).forGetter(ModuleDefinition::energyCapacity),
            Codec.intRange(0, 15).optionalFieldOf("light_cap", 15).forGetter(ModuleDefinition::lightCap),
            Codec.intRange(0, 100_000).optionalFieldOf("output_capacity", 0).forGetter(ModuleDefinition::outputCapacity)
    ).apply(instance, ModuleDefinition::new));
}
