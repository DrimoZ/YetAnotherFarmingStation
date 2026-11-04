package dev.drimoz.yafs.client.compat.jei;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.client.greenhouse.GreenhouseScreen;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.registry.YafsItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@JeiPlugin
public class YafsJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
    }

    public static void showCrops() {
        if (runtime != null) runtime.getRecipesGui().showTypes(List.of(CropCategory.TYPE));
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CropCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Crop> crops = new ArrayList<>(Crops.all().values());
        crops.sort(Comparator.comparing(crop -> BuiltInRegistries.ITEM.getKey(crop.seed())));
        registration.addRecipes(CropCategory.TYPE, crops);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(CropCategory.TYPE, YafsItems.GREENHOUSE_T1.get(), YafsItems.GREENHOUSE_T2.get(), YafsItems.GREENHOUSE_T3.get());
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
