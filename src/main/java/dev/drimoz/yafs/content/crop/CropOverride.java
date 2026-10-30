package dev.drimoz.yafs.content.crop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;

/**
 * Une entrée {@code data/<ns>/yafs/crop/*.json} : ce qu'un datapack dit d'une culture. Chaque
 * champ présent remplace ce que la détection a trouvé ; un champ absent le laisse.
 *
 * <pre>{@code
 * {
 *   "seed": "mysticalagriculture:nether_star_seeds",
 *   "substrates": ["#minecraft:dirt", "minecraft:farmland"],
 *   "catalyst": "mysticalagriculture:nether_star_crux",
 *   "requirements": { "min_light": 9, "min_temperature": 25, "max_temperature": 40 },
 *   "harvest": "minecraft:melon"
 * }
 * }</pre>
 *
 * <p>{@code substrates} : chaque élément est un item ou un {@code #tag}, mélangeables.
 * {@code harvest} : le bloc dont la table de loot donne la récolte, quand ce n'est pas la culture
 * mûre elle-même ; une tige de melon ne lâche que des graines, c'est le melon qu'on récolte.
 */
public record CropOverride(Item seed, Optional<List<ItemMatch>> substrates, Optional<Item> catalyst,
                           Optional<Requirements> requirements, Optional<Block> harvest) {

    public static final Codec<CropOverride> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("seed").forGetter(CropOverride::seed),
            ItemMatch.CODEC.listOf().optionalFieldOf("substrates").forGetter(CropOverride::substrates),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("catalyst").forGetter(CropOverride::catalyst),
            Requirements.CODEC.optionalFieldOf("requirements").forGetter(CropOverride::requirements),
            BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("harvest").forGetter(CropOverride::harvest)
    ).apply(instance, CropOverride::new));
}
