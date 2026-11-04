package dev.drimoz.yafs.client;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.client.greenhouse.GreenhouseModel;
import dev.drimoz.yafs.client.greenhouse.GreenhouseRenderer;
import dev.drimoz.yafs.client.greenhouse.GreenhouseScreen;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsMenus;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.List;

@EventBusSubscriber(modid = YetAnotherFarmingStation.MOD_ID, value = Dist.CLIENT)
public final class YafsClient {

    private YafsClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(YafsBlocks.GREENHOUSE_ENTITY.get(), GreenhouseRenderer::new);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        for (Block block : List.of(YafsBlocks.GREENHOUSE_T1.get(), YafsBlocks.GREENHOUSE_T2.get(), YafsBlocks.GREENHOUSE_T3.get())) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> new GreenhouseModel(model));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(YafsMenus.GREENHOUSE.get(), GreenhouseScreen::new);
    }
}
