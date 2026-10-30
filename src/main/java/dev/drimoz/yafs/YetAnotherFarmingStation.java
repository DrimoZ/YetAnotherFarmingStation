package dev.drimoz.yafs;

import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsCreativeTabs;
import dev.drimoz.yafs.registry.YafsItems;
import dev.drimoz.yafs.registry.YafsMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(YetAnotherFarmingStation.MOD_ID)
public class YetAnotherFarmingStation {

    public static final String MOD_ID = "yafs";

    public YetAnotherFarmingStation(IEventBus modEventBus, ModContainer modContainer) {
        YafsBlocks.BLOCKS.register(modEventBus);
        YafsBlocks.BLOCK_ENTITIES.register(modEventBus);
        YafsItems.ITEMS.register(modEventBus);
        YafsCreativeTabs.TABS.register(modEventBus);
        YafsMenus.MENUS.register(modEventBus);

        modEventBus.addListener(MultiblockPartBlockEntity::registerCapabilities);
    }
}
