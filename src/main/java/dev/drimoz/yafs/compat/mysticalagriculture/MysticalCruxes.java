package dev.drimoz.yafs.compat.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.MysticalAgricultureAPI;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.ItemMatch;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.Map;

public final class MysticalCruxes {

    public static final String MOD_ID = "mysticalagriculture";

    private MysticalCruxes() {}

    public static void apply(Map<Item, Crop> found) {
        for (var crop : MysticalAgricultureAPI.getCropRegistry().getCrops()) {
            if (!crop.isEnabled() || crop.getCruxBlock() == null) continue;
            Crop detected = found.get(crop.getSeedsItem());
            if (detected == null) continue;
            found.put(detected.seed(), new Crop(detected.seed(), detected.block(), detected.age(), detected.substrates(),
                    detected.requirements(), new ItemMatch(crop.getCruxBlock().asItem(), null), detected.harvest(), detected.cost()));
        }
    }

    public static String census() {
        var crops = MysticalAgricultureAPI.getCropRegistry().getCrops();
        long enabled = crops.stream().filter(crop -> crop.isEnabled()).count();
        StringBuilder cruxes = new StringBuilder();
        crops.stream().filter(crop -> crop.getCruxBlock() != null).forEach(crop -> cruxes.append(crop.getName()).append(crop.isEnabled() ? "+ " : "- "));
        return crops.size() + " cultures, " + enabled + " activées, à crux : " + cruxes;
    }

    @Nullable
    public static Item[] anyCruxSeed() {
        for (var crop : MysticalAgricultureAPI.getCropRegistry().getCrops()) {
            if (crop.isEnabled() && crop.getCruxBlock() != null) return new Item[]{crop.getSeedsItem(), crop.getCruxBlock().asItem()};
        }
        return null;
    }
}
