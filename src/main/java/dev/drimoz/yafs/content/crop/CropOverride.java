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
 *
 * <p>{@code residue_chance} : la chance de compost par plant et par cycle (0,2 par défaut). {@code residue_item} : ce que
 * laisse ce résidu, le compost du mod par défaut.
 * {@code ticks}, {@code water_per_cycle}, {@code energy_per_tick} : ce que coûte un cycle de
 * cette culture, voir {@link Crop.Cost}. Une graine de Mystical Agriculture peut ainsi prendre
 * deux fois plus longtemps et coûter dix fois plus qu'un blé.
 */
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
