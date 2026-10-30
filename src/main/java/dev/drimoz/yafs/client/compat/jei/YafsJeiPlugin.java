package dev.drimoz.yafs.client.compat.jei;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.client.greenhouse.GreenhouseScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * JEI ne recouvre pas les onglets de la serre. Chargée par JEI seul : sans JEI, la classe n'est
 * jamais lue, et le mod tourne pareil. Les pages de cultures viendront avec YAF-080.
 */
@JeiPlugin
public class YafsJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GreenhouseScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(GreenhouseScreen screen) {
                return screen.tabAreas();
            }
        });
    }
}
