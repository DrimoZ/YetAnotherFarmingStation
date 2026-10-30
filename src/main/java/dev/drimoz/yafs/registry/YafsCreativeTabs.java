package dev.drimoz.yafs.registry;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YafsCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YetAnotherFarmingStation.MOD_ID);

    /**
     * Chaque item du mod, dans l'ordre d'enregistrement : rien à tenir à jour à la main. Les
     * modules sont énumérés depuis leur registre, ceux d'un datapack compris.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.yafs"))
                    .icon(() -> new ItemStack(YafsItems.GREENHOUSE_T1.get()))
                    .displayItems((parameters, output) -> {
                        YafsItems.ITEMS.getEntries().stream()
                                .filter(item -> item.get() != YafsItems.MODULE.get())
                                .forEach(item -> output.accept(item.get()));
                        parameters.holders().lookup(ModuleDefinition.REGISTRY).ifPresent(modules -> modules.listElementIds()
                                .forEach(key -> {
                                    ItemStack module = new ItemStack(YafsItems.MODULE.get());
                                    module.set(YafsComponents.MODULE.get(), key);
                                    output.accept(module);
                                }));
                    })
                    .build());

    private YafsCreativeTabs() {}
}
