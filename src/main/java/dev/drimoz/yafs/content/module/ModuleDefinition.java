package dev.drimoz.yafs.content.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.crop.ItemMatch;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public record ModuleDefinition(int lightFloor, double temperatureShift, int rainCollection, int energyPerTick,
                               double speedMultiplier, double energyMultiplier, double waterMultiplier, int density, int maxStack,
                               int energyCapacity, int lightCap, int outputCapacity, int composting,
                               Optional<ItemMatch> compostInput, int compostCount, Optional<Item> compostOutput) {

    public boolean excludes(ModuleDefinition other) {
        boolean lights = this.lightFloor > 0 && other.lightFloor > 0;
        boolean darks = this.lightCap < 15 && other.lightCap < 15;
        boolean opposed = (this.lightFloor > 0 && other.lightCap < 15) || (this.lightCap < 15 && other.lightFloor > 0);
        return lights || darks || opposed;
    }

    public int extraPlants() {
        return this.density - 1;
    }

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
            Codec.intRange(0, 100_000).optionalFieldOf("output_capacity", 0).forGetter(ModuleDefinition::outputCapacity),
            Codec.intRange(0, 64).optionalFieldOf("composting", 0).forGetter(ModuleDefinition::composting),
            ItemMatch.CODEC.optionalFieldOf("compost_input").forGetter(ModuleDefinition::compostInput),
            Codec.intRange(1, 64).optionalFieldOf("compost_count", 4).forGetter(ModuleDefinition::compostCount),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("compost_output").forGetter(ModuleDefinition::compostOutput)
    ).apply(instance, ModuleDefinition::new));
}
