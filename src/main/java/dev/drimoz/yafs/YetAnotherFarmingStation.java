package dev.drimoz.yafs;

import dev.drimoz.yafs.content.crop.CropData;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.fertilizer.FertilizerDefinition;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsCreativeTabs;
import dev.drimoz.yafs.registry.YafsItems;
import dev.drimoz.yafs.registry.YafsMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

@Mod(YetAnotherFarmingStation.MOD_ID)
public class YetAnotherFarmingStation {

    public static final String MOD_ID = "yafs";

    public YetAnotherFarmingStation(IEventBus modEventBus, ModContainer modContainer) {
        YafsBlocks.BLOCKS.register(modEventBus);
        YafsBlocks.BLOCK_ENTITIES.register(modEventBus);
        YafsItems.ITEMS.register(modEventBus);
        YafsCreativeTabs.TABS.register(modEventBus);
        YafsMenus.MENUS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, YafsConfig.SPEC);

        modEventBus.addListener(MultiblockPartBlockEntity::registerCapabilities);
        modEventBus.addListener(GreenhouseBlockEntity::registerCapabilities);
        modEventBus.addListener(CropData::onRegisterPayloads);
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            event.dataPackRegistry(ModuleDefinition.REGISTRY, ModuleDefinition.CODEC, ModuleDefinition.CODEC);
            event.dataPackRegistry(FertilizerDefinition.REGISTRY, FertilizerDefinition.CODEC, FertilizerDefinition.CODEC);
        });
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> Crops.invalidate());
        NeoForge.EVENT_BUS.addListener(CropData::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(CropData::onDatapackSync);
    }
}
