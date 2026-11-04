package dev.drimoz.yafs.content.crop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;

public record CropOverride(ItemMatch seed, Optional<List<ItemMatch>> substrates, Optional<ItemMatch> catalyst,
                           Optional<Requirements> requirements, Optional<Block> harvest,
                           Optional<Integer> ticks, Optional<Integer> waterPerCycle, Optional<Integer> energyPerTick,
                           Optional<Double> residueChance,
                           Optional<Item> residueItem) {

    public static final Codec<CropOverride> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemMatch.CODEC.fieldOf("seed").forGetter(CropOverride::seed),
            ItemMatch.CODEC.listOf().optionalFieldOf("substrates").forGetter(CropOverride::substrates),
            ItemMatch.CODEC.optionalFieldOf("catalyst").forGetter(CropOverride::catalyst),
            Requirements.CODEC.optionalFieldOf("requirements").forGetter(CropOverride::requirements),
            BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("harvest").forGetter(CropOverride::harvest),
            Codec.intRange(20, 10_000_000).optionalFieldOf("ticks").forGetter(CropOverride::ticks),
            Codec.intRange(0, 1_000_000).optionalFieldOf("water_per_cycle").forGetter(CropOverride::waterPerCycle),
            Codec.intRange(0, 1_000_000).optionalFieldOf("energy_per_tick").forGetter(CropOverride::energyPerTick),
            Codec.doubleRange(0D, 1D).optionalFieldOf("residue_chance").forGetter(CropOverride::residueChance),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("residue_item").forGetter(CropOverride::residueItem)
    ).apply(instance, CropOverride::new));
}
