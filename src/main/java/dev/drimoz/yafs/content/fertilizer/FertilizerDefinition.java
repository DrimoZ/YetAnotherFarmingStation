package dev.drimoz.yafs.content.fertilizer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import dev.drimoz.yafs.content.crop.ItemMatch;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public record FertilizerDefinition(ItemMatch item, double speed, double yield, int seconds) {

    public static final ResourceKey<Registry<FertilizerDefinition>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "fertilizer"));

    public static final Codec<FertilizerDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemMatch.CODEC.fieldOf("item").forGetter(FertilizerDefinition::item),
            Codec.doubleRange(1D, 16D).optionalFieldOf("speed", 1D).forGetter(FertilizerDefinition::speed),
            Codec.doubleRange(1D, 16D).optionalFieldOf("yield", 1D).forGetter(FertilizerDefinition::yield),
            Codec.intRange(1, 1_000_000).optionalFieldOf("seconds", 300).forGetter(FertilizerDefinition::seconds)
    ).apply(instance, FertilizerDefinition::new));

    @Nullable
    public static FertilizerDefinition of(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        if (stack.isEmpty() || registries == null) return null;
        return registries.lookup(REGISTRY).flatMap(lookup -> lookup.listElements()
                .map(holder -> holder.value())
                .filter(definition -> definition.item().contains(stack.getItem()))
                .findFirst()).orElse(null);
    }
}
