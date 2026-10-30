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

    public static final DeferredItem<BlockItem> CREATIVE_ENERGY_SOURCE = ITEMS.registerSimpleBlockItem(YafsBlocks.CREATIVE_ENERGY_SOURCE);
    public static final DeferredItem<PortItem> PORT = ITEMS.registerItem("port", PortItem::new);

    public static final DeferredItem<ModuleItem> GROW_LAMP = ITEMS.registerItem("grow_lamp", ModuleItem::new);
    public static final DeferredItem<ModuleItem> HEATER = ITEMS.registerItem("heater", ModuleItem::new);
    public static final DeferredItem<ModuleItem> COOLER = ITEMS.registerItem("cooler", ModuleItem::new);
    public static final DeferredItem<ModuleItem> RAIN_COLLECTOR = ITEMS.registerItem("rain_collector", ModuleItem::new);
    public static final DeferredItem<ModuleItem> ACCELERATOR_1 = ITEMS.registerItem("accelerator_1", ModuleItem::new);
    public static final DeferredItem<ModuleItem> ACCELERATOR_2 = ITEMS.registerItem("accelerator_2", ModuleItem::new);
    public static final DeferredItem<ModuleItem> ACCELERATOR_3 = ITEMS.registerItem("accelerator_3", ModuleItem::new);
    public static final DeferredItem<ModuleItem> DENSITY_1 = ITEMS.registerItem("density_1", ModuleItem::new);
    public static final DeferredItem<ModuleItem> DENSITY_2 = ITEMS.registerItem("density_2", ModuleItem::new);
    public static final DeferredItem<ModuleItem> DENSITY_3 = ITEMS.registerItem("density_3", ModuleItem::new);

    /** Les modules, pour ce qui les traite tous pareil (teinte, onglet). */
    public static List<DeferredItem<ModuleItem>> modules() {
        return List.of(GROW_LAMP, HEATER, COOLER, RAIN_COLLECTOR, ACCELERATOR_1, ACCELERATOR_2, ACCELERATOR_3, DENSITY_1, DENSITY_2, DENSITY_3);
    }
    private YafsItems() {}
}
