package dev.drimoz.yafs.content.module;

import dev.drimoz.yafs.registry.YafsComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Un seul item pour tous les modules : son identité est le composant {@code yafs:module}, qui
 * pointe vers une {@link ModuleDefinition}. Un datapack ajoute un module sans écrire de code. Le
 * modèle de Portable Beacons pour ses augments.
 */
public class ModuleItem extends Item {

    public ModuleItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static ResourceKey<ModuleDefinition> kind(ItemStack stack) {
        return stack.get(YafsComponents.MODULE.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceKey<ModuleDefinition> kind = kind(stack);
        if (kind == null) return super.getName(stack);
        return Component.translatable("module." + kind.location().getNamespace() + "." + kind.location().getPath());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceKey<ModuleDefinition> kind = kind(stack);
        if (kind == null || context.registries() == null) return;

        context.registries().lookup(ModuleDefinition.REGISTRY).flatMap(lookup -> lookup.get(kind)).ifPresent(holder -> {
            ModuleDefinition module = holder.value();
            if (module.lightFloor() > 0) tooltip.add(Component.translatable("yafs.module.light_floor", module.lightFloor()));
            if (module.temperatureShift() != 0) tooltip.add(Component.translatable("yafs.module.temperature_shift", String.format("%+.0f", module.temperatureShift())));
            if (module.rainCollection() > 0) tooltip.add(Component.translatable("yafs.module.rain_collection", module.rainCollection()));
            if (module.energyPerTick() > 0) tooltip.add(Component.translatable("yafs.module.energy_per_tick", module.energyPerTick()));
        });
    }
}
