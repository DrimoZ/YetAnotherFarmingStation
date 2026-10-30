package dev.drimoz.yafs.registry;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YafsCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YetAnotherFarmingStation.MOD_ID);

    /** Chaque item du mod, dans l'ordre d'enregistrement : rien à tenir à jour à la main. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.yafs"))
                    .icon(() -> new ItemStack(YafsItems.GREENHOUSE_T1.get()))
                    .displayItems((parameters, output) -> YafsItems.ITEMS.getEntries()
                            .forEach(item -> output.accept(item.get())))
                    .build());

    private YafsCreativeTabs() {}
}
