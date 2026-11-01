package dev.drimoz.yafs.content.fertilizer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Ce que fait un engrais (YAF-022), défini en datapack : {@code data/<ns>/yafs/fertilizer/*.json}.
 * Une serre n'en prend qu'une sorte à la fois, donc il accélère <b>ou</b> il rend plus, jamais les
 * deux : choisir est tout l'intérêt.
 *
 * <pre>{@code
 * { "item": "minecraft:bone_meal", "speed": 1.5, "cycles": 8 }
 * }</pre>
 *
 * @param item   l'item qui sert d'engrais
 * @param speed  vitesse de toutes les parcelles, multipliée (1 : sans effet)
 * @param yield  récolte de toutes les parcelles, multipliée (1 : sans effet)
 * @param cycles combien de cycles de plant un item nourrit : consommé à la mesure de la production,
 *               pas à la seconde, donc une serre à l'arrêt n'en use pas
 */
public record FertilizerDefinition(Item item, double speed, double yield, int cycles) {

    public static final ResourceKey<Registry<FertilizerDefinition>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "fertilizer"));

    public static final Codec<FertilizerDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(FertilizerDefinition::item),
            Codec.doubleRange(1D, 16D).optionalFieldOf("speed", 1D).forGetter(FertilizerDefinition::speed),
            Codec.doubleRange(1D, 16D).optionalFieldOf("yield", 1D).forGetter(FertilizerDefinition::yield),
            Codec.intRange(1, 1_000_000).optionalFieldOf("cycles", 8).forGetter(FertilizerDefinition::cycles)
    ).apply(instance, FertilizerDefinition::new));

    /**
     * L'engrais que cet item est, s'il en est un. Parcourt le registre : à n'appeler qu'au changement
     * du contenu de l'emplacement, pas à chaque tick.
     */
    @Nullable
    public static FertilizerDefinition of(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        if (stack.isEmpty() || registries == null) return null;
        return registries.lookup(REGISTRY).flatMap(lookup -> lookup.listElements()
                .map(holder -> holder.value())
                .filter(definition -> definition.item() == stack.getItem())
                .findFirst()).orElse(null);
    }
}
