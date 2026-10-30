package dev.drimoz.yafs.registry;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.PortItem;
import dev.drimoz.yafs.content.module.ModuleItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class YafsItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YetAnotherFarmingStation.MOD_ID);

    public static final DeferredItem<BlockItem> GREENHOUSE_T1 = ITEMS.registerSimpleBlockItem(YafsBlocks.GREENHOUSE_T1);
    public static final DeferredItem<BlockItem> GREENHOUSE_T2 = ITEMS.registerSimpleBlockItem(YafsBlocks.GREENHOUSE_T2);
    public static final DeferredItem<BlockItem> GREENHOUSE_T3 = ITEMS.registerSimpleBlockItem(YafsBlocks.GREENHOUSE_T3);

    public static final DeferredItem<PortItem> PORT = ITEMS.registerItem("port", PortItem::new);

    public static final DeferredItem<ModuleItem> GROW_LAMP = ITEMS.registerItem("grow_lamp", ModuleItem::new);
    public static final DeferredItem<ModuleItem> HEATER = ITEMS.registerItem("heater", ModuleItem::new);
    public static final DeferredItem<ModuleItem> COOLER = ITEMS.registerItem("cooler", ModuleItem::new);
    public static final DeferredItem<ModuleItem> RAIN_COLLECTOR = ITEMS.registerItem("rain_collector", ModuleItem::new);

    /** Les modules, pour ce qui les traite tous pareil (teinte, onglet). */
    public static List<DeferredItem<ModuleItem>> modules() {
        return List.of(GROW_LAMP, HEATER, COOLER, RAIN_COLLECTOR);
    }
    private YafsItems() {}
}
