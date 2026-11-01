package dev.drimoz.yafs.content.crop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Une entrée {@code data/<ns>/yafs/substrate/*.json} : des items qui, dans une parcelle, comptent
 * comme un autre bloc de sol. C'est ainsi que la terre vaut terre labourée irriguée, sans houe.
 *
 * <pre>{@code
 * { "items": ["minecraft:dirt", "minecraft:grass_block"], "counts_as": "minecraft:farmland" }
 * }</pre>
 */
public record SubstrateDefinition(List<ItemMatch> items, Block countsAs, double speed, double yield) {

    /** Ce qu'un sol apporte à sa parcelle. */
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
