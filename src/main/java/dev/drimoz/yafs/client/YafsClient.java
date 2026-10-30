package dev.drimoz.yafs.client;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.client.greenhouse.GreenhouseRenderer;
import dev.drimoz.yafs.client.greenhouse.GreenhouseScreen;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ItemLike;
import dev.drimoz.yafs.registry.YafsMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Ce que le client enregistre sur le bus du mod. */
// Le bus du mod est déduit du type d'événement : l'attribut bus est déprécié.
@EventBusSubscriber(modid = YetAnotherFarmingStation.MOD_ID, value = Dist.CLIENT)
public final class YafsClient {

    private YafsClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(YafsBlocks.GREENHOUSE_ENTITY.get(), GreenhouseRenderer::new);
    }

    /** La teinte d'un module vient de sa définition : une texture commune, une couleur par module. */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> {
            ResourceKey<ModuleDefinition> kind = ModuleItem.kind(stack);
            ClientLevel level = Minecraft.getInstance().level;
            if (layer != 0 || kind == null || level == null) return 0xFFFFFFFF;
            return level.registryAccess().registry(ModuleDefinition.REGISTRY)
                    .map(registry -> registry.get(kind))
                    .map(module -> 0xFF000000 | module.tint())
                    .orElse(0xFFFFFFFF);
        }, YafsItems.modules().stream().map(item -> item.get()).toArray(ItemLike[]::new));
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(YafsMenus.GREENHOUSE.get(), GreenhouseScreen::new);
    }
}
