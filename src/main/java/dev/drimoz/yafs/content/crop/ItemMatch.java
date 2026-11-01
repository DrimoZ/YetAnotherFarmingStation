package dev.drimoz.yafs.content.crop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Un item ou un {@code #tag} d'items, écrit comme une chaîne dans un JSON.
 *
 * <p>Pas un {@code HolderSet} : les JSON se lisent pendant le rechargement, avant que les tags du
 * même rechargement soient liés, et un {@code HolderSet} refuse alors tout tag. Le tag est gardé
 * par son nom et résolu à la détection, quand il existe.
 */
public record ItemMatch(@Nullable Item item, @Nullable TagKey<Item> tag) {

    public static final Codec<ItemMatch> CODEC = Codec.STRING.comapFlatMap(ItemMatch::parse, ItemMatch::write);

    private static DataResult<ItemMatch> parse(String text) {
        boolean isTag = text.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(isTag ? text.substring(1) : text);
        if (id == null) return DataResult.error(() -> "Identifiant invalide : " + text);
        if (isTag) return DataResult.success(new ItemMatch(null, TagKey.create(Registries.ITEM, id)));

        return BuiltInRegistries.ITEM.getOptional(id)
                .map(item -> DataResult.success(new ItemMatch(item, null)))
                .orElseGet(() -> DataResult.error(() -> "Item inconnu : " + text));
    }

    private String write() {
        return this.tag != null ? "#" + this.tag.location() : BuiltInRegistries.ITEM.getKey(this.item).toString();
    }

    /** Cet item est-il désigné ? Sans parcourir le tag : un item sait de quels tags il est. */
    public boolean contains(Item candidate) {
        return this.item != null ? this.item == candidate : candidate.builtInRegistryHolder().is(this.tag);
    }

    /** Les items désignés ; un tag absent n'en désigne aucun. */
    public Stream<Item> items() {
        if (this.item != null) return Stream.of(this.item);
        return StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(this.tag).spliterator(), false).map(Holder::value);
    }
}
