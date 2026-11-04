package dev.drimoz.yafs.content.crop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.List;

public record SubstrateDefinition(List<ItemMatch> items, Block countsAs, double speed, double yield) {

    public record Bonus(double speed, double yield) {

        public static final Bonus NONE = new Bonus(1D, 1D);
    }

    public Bonus bonus() {
        return new Bonus(this.speed, this.yield);
    }

    public static final Codec<SubstrateDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemMatch.CODEC.listOf().fieldOf("items").forGetter(SubstrateDefinition::items),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("counts_as").forGetter(SubstrateDefinition::countsAs),
            Codec.doubleRange(1D, 16D).optionalFieldOf("speed", 1D).forGetter(SubstrateDefinition::speed),
            Codec.doubleRange(1D, 16D).optionalFieldOf("yield", 1D).forGetter(SubstrateDefinition::yield)
    ).apply(instance, SubstrateDefinition::new));
}
