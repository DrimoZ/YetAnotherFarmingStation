package dev.drimoz.yafs.content.crop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Une entrée {@code data/<ns>/yafs/substrate/*.json} : des items qui, dans une parcelle, comptent
 * comme un autre bloc de sol. C'est ainsi que la terre vaut terre labourée irriguée, sans houe.
 *
 * <pre>{@code
 * { "items": ["minecraft:dirt", "minecraft:grass_block"], "counts_as": "minecraft:farmland" }
 * }</pre>
 */
public record SubstrateDefinition(HolderSet<Item> items, Block countsAs) {

    public static final Codec<SubstrateDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(SubstrateDefinition::items),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("counts_as").forGetter(SubstrateDefinition::countsAs)
    ).apply(instance, SubstrateDefinition::new));
}
