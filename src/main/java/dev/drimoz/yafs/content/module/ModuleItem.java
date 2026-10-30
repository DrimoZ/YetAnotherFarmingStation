package dev.drimoz.yafs.content.module;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
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
 * qu'un datapack peut retoucher. Empilés dans un emplacement, ils combinent leurs effets.
 */
public class ModuleItem extends Item {


    public ModuleItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    /** Combien de ce module une case accepte, d'après sa définition ; 1 si on ne la trouve pas. */
    public static int maxStack(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        ResourceKey<ModuleDefinition> kind = kind(stack);
        if (kind == null || registries == null) return 1;
        return registries.lookup(ModuleDefinition.REGISTRY).flatMap(lookup -> lookup.get(kind))
                .map(holder -> holder.value().maxStack()).orElse(1);
    }

    /** La définition de ce module, nommée comme son item ; {@code null} si ce n'est pas un module. */
    @Nullable
    public static ResourceKey<ModuleDefinition> kind(ItemStack stack) {
        return stack.getItem() instanceof ModuleItem
                ? ResourceKey.create(ModuleDefinition.REGISTRY, BuiltInRegistries.ITEM.getKey(stack.getItem()))
                : null;
    }

    /** Ce que fait un module, gains en vert, coûts en or ; puis où il se pose. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("yafs.tooltip.module").withStyle(ChatFormatting.BLUE));

        ResourceKey<ModuleDefinition> kind = kind(stack);
        if (kind != null && context.registries() != null) {
            context.registries().lookup(ModuleDefinition.REGISTRY).flatMap(lookup -> lookup.get(kind)).ifPresent(holder -> {
                ModuleDefinition module = holder.value();
                if (module.speedMultiplier() > 1D) gain(tooltip, "yafs.module.speed", times(module.speedMultiplier()));
                if (module.density() > 1) gain(tooltip, "yafs.module.density", module.density());
                if (module.lightFloor() > 0) gain(tooltip, "yafs.module.light_floor", module.lightFloor());
                if (module.temperatureShift() != 0) gain(tooltip, "yafs.module.temperature_shift", String.format("%+.0f", module.temperatureShift()));
                if (module.rainCollection() > 0) gain(tooltip, "yafs.module.rain_collection", module.rainCollection());
                if (module.energyMultiplier() > 1D) cost(tooltip, "yafs.module.energy_cost", times(module.energyMultiplier()));
                if (module.waterMultiplier() > 1D) cost(tooltip, "yafs.module.water_cost", times(module.waterMultiplier()));
                if (module.energyPerTick() > 0) cost(tooltip, "yafs.module.energy_per_tick", module.energyPerTick());
                if (module.speedMultiplier() > 1D || module.energyPerTick() > 0) {
                    tooltip.add(Component.translatable("yafs.tooltip.module.needs_energy").withStyle(ChatFormatting.DARK_GRAY));
                }
                tooltip.add(Component.translatable(module.maxStack() > 1 ? "yafs.tooltip.module.stack" : "yafs.tooltip.module.single", module.maxStack())
                        .withStyle(ChatFormatting.DARK_GRAY));
            });
        }
    }

    private static void gain(List<Component> tooltip, String key, Object value) {
        tooltip.add(Component.literal(" + ").append(Component.translatable(key, value)).withStyle(ChatFormatting.GREEN));
    }

    private static void cost(List<Component> tooltip, String key, Object value) {
        tooltip.add(Component.literal(" − ").append(Component.translatable(key, value)).withStyle(ChatFormatting.GOLD));
    }

    private static String times(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
