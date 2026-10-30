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
 * { "temperature_shift": 20, "energy_per_tick": 40, "tint": 14701338 }
 * }</pre>
 *
 * @param lightFloor        la lumière ne descend pas sous ce niveau tant que le module tourne
 * @param temperatureShift  degrés ajoutés à la température du lieu (négatif pour refroidir)
 * @param rainCollection    mB recueillis par pas de croissance quand il pleut sur la serre
 * @param energyPerTick     FE consommés par tick ; sans énergie, le module ne fait rien
 * @param tint              la teinte de l'item, en RGB
 */
public record ModuleDefinition(int lightFloor, double temperatureShift, int rainCollection, int energyPerTick, int tint) {

    public static final ResourceKey<Registry<ModuleDefinition>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "module"));

    public static final Codec<ModuleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 15).optionalFieldOf("light_floor", 0).forGetter(ModuleDefinition::lightFloor),
            Codec.doubleRange(-60D, 60D).optionalFieldOf("temperature_shift", 0D).forGetter(ModuleDefinition::temperatureShift),
            Codec.intRange(0, 1_000).optionalFieldOf("rain_collection", 0).forGetter(ModuleDefinition::rainCollection),
            Codec.intRange(0, 10_000).optionalFieldOf("energy_per_tick", 0).forGetter(ModuleDefinition::energyPerTick),
            Codec.INT.optionalFieldOf("tint", 0xFFFFFF).forGetter(ModuleDefinition::tint)
    ).apply(instance, ModuleDefinition::new));
}
