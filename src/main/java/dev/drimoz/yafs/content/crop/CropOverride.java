package dev.drimoz.yafs.content.crop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

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
 *   "requirements": { "min_light": 9, "min_temperature": 25, "max_temperature": 40 }
 * }
 * }</pre>
 */
public record CropOverride(Item seed, Optional<HolderSet<Item>> substrates, Optional<Item> catalyst,
                           Optional<Requirements> requirements) {

    public static final Codec<CropOverride> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("seed").forGetter(CropOverride::seed),
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("substrates").forGetter(CropOverride::substrates),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("catalyst").forGetter(CropOverride::catalyst),
            Requirements.CODEC.optionalFieldOf("requirements").forGetter(CropOverride::requirements)
    ).apply(instance, CropOverride::new));
}
