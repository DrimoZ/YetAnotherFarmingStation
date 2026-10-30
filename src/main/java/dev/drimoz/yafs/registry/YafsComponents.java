package dev.drimoz.yafs.registry;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YafsComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, YetAnotherFarmingStation.MOD_ID);

    /** Quel module est cet item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceKey<ModuleDefinition>>> MODULE =
            COMPONENTS.registerComponentType("module", builder -> builder
                    .persistent(ResourceKey.codec(ModuleDefinition.REGISTRY))
                    .networkSynchronized(ResourceKey.streamCodec(ModuleDefinition.REGISTRY)));

    private YafsComponents() {}
}
