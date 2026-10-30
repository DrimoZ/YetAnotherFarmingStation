package dev.drimoz.yafs.content.module;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Un module de serre : un item par module, comme un bloc par palier. Ce qu'il fait vient de la
 * {@link ModuleDefinition} de même identifiant ({@code yafs:heater} → {@code data/yafs/yafs/module/heater.json}),
 * qu'un datapack peut retoucher.
 */
public class ModuleItem extends Item {

    public ModuleItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    /** La définition de ce module, nommée comme son item ; {@code null} si ce n'est pas un module. */
    @Nullable
    public static ResourceKey<ModuleDefinition> kind(ItemStack stack) {
        return stack.getItem() instanceof ModuleItem
                ? ResourceKey.create(ModuleDefinition.REGISTRY, BuiltInRegistries.ITEM.getKey(stack.getItem()))
                : null;
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
            if (module.speedMultiplier() > 1D) tooltip.add(Component.translatable("yafs.module.speed", times(module.speedMultiplier())));
            if (module.density() > 1) tooltip.add(Component.translatable("yafs.module.density", module.density()));
            if (module.energyMultiplier() > 1D) tooltip.add(Component.translatable("yafs.module.energy_cost", times(module.energyMultiplier())));
            if (module.waterMultiplier() > 1D) tooltip.add(Component.translatable("yafs.module.water_cost", times(module.waterMultiplier())));
        });
    }

    private static String times(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
