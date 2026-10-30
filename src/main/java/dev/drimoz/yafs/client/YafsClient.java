package dev.drimoz.yafs.client;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.client.greenhouse.GreenhouseRenderer;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Ce que le client enregistre sur le bus du mod. */
// Le bus du mod est déduit du type d'événement : l'attribut bus est déprécié.
@EventBusSubscriber(modid = YetAnotherFarmingStation.MOD_ID, value = Dist.CLIENT)
public final class YafsClient {

    private YafsClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(YafsBlocks.GREENHOUSE_ENTITY.get(), GreenhouseRenderer::new);
    }
}
