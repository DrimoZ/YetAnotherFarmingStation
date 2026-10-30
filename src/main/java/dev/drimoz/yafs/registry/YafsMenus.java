package dev.drimoz.yafs.registry;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YafsMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, YetAnotherFarmingStation.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<GreenhouseMenu>> GREENHOUSE =
            MENUS.register("greenhouse", () -> IMenuTypeExtension.create(GreenhouseMenu::new));

    private YafsMenus() {}
}
