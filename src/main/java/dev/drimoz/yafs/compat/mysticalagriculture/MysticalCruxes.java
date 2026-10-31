package dev.drimoz.yafs.compat.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.MysticalAgricultureAPI;
import dev.drimoz.yafs.content.crop.Crop;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Mystical Agriculture (YAF-082) : une graine à crux ne pousse que sur son crux, posé sous la
 * terre. Dans la serre, le crux est le catalyseur de la parcelle.
 *
 * <p>Appelée seulement si Mystical Agriculture est chargé ({@link #MOD_ID}) : sans lui, la classe
 * n'est jamais chargée. ponytail: les biomes exigés de certaines graines sont ignorés ; la serre
 * fait son propre climat.
 */
public final class MysticalCruxes {

    public static final String MOD_ID = "mysticalagriculture";

    private MysticalCruxes() {}

    /** Donne à chaque graine à crux que la détection a trouvée son crux pour catalyseur. */
    public static void apply(Map<Item, Crop> found) {
        for (var crop : MysticalAgricultureAPI.getCropRegistry().getCrops()) {
            if (!crop.isEnabled() || crop.getCruxBlock() == null) continue;
            Crop detected = found.get(crop.getSeedsItem());
            if (detected == null) continue;
            found.put(detected.seed(), new Crop(detected.seed(), detected.block(), detected.age(), detected.substrates(),
                    detected.requirements(), crop.getCruxBlock().asItem(), detected.harvest(), detected.cost()));
        }
    }

    /** Pour le message d'un test qui échoue : cultures, activées, à crux. */
    public static String census() {
        var crops = MysticalAgricultureAPI.getCropRegistry().getCrops();
        long enabled = crops.stream().filter(crop -> crop.isEnabled()).count();
        StringBuilder cruxes = new StringBuilder();
        crops.stream().filter(crop -> crop.getCruxBlock() != null).forEach(crop -> cruxes.append(crop.getName()).append(crop.isEnabled() ? "+ " : "- "));
        return crops.size() + " cultures, " + enabled + " activées, à crux : " + cruxes;
    }

    /** Une graine à crux et son crux, pour les GameTests ; {@code null} s'il n'y en a pas. */
    @Nullable
    public static Item[] anyCruxSeed() {
        for (var crop : MysticalAgricultureAPI.getCropRegistry().getCrops()) {
            if (crop.isEnabled() && crop.getCruxBlock() != null) return new Item[]{crop.getSeedsItem(), crop.getCruxBlock().asItem()};
        }
        return null;
    }
}
