package dev.drimoz.yafs;

import dev.drimoz.yafs.content.crop.CropData;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsCreativeTabs;
import dev.drimoz.yafs.registry.YafsItems;
import dev.drimoz.yafs.registry.YafsMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

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
        modEventBus.addListener(CropData::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> Crops.invalidate());
        NeoForge.EVENT_BUS.addListener(CropData::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(CropData::onDatapackSync);
    }
}
