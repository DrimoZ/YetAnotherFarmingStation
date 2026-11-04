package dev.drimoz.yafs.content.module;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;

public class ModuleItem extends Item {

    public ModuleItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    public static int maxStack(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        ModuleDefinition module = definition(stack, registries);
        return module == null ? 1 : module.maxStack();
    }

    @Nullable
    public static ModuleDefinition definition(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        ResourceKey<ModuleDefinition> kind = kind(stack);
        if (kind == null || registries == null) return null;
        return registries.lookup(ModuleDefinition.REGISTRY).flatMap(lookup -> lookup.get(kind)).map(holder -> holder.value()).orElse(null);
    }

    public static boolean fits(IItemHandler modules, int slot, ItemStack stack, @Nullable HolderLookup.Provider registries) {
        ResourceKey<ModuleDefinition> kind = kind(stack);
        if (kind == null) return false;
        ModuleDefinition module = definition(stack, registries);
        for (int other = 0; other < modules.getSlots(); other++) {
            ItemStack placed = modules.getStackInSlot(other);
            if (other == slot || placed.isEmpty()) continue;
            if (kind.equals(kind(placed))) return false;
            ModuleDefinition rival = definition(placed, registries);
            if (module != null && rival != null && module.excludes(rival)) return false;
        }
        return true;
    }

    @Nullable
    public static ResourceKey<ModuleDefinition> kind(ItemStack stack) {
        return stack.getItem() instanceof ModuleItem
                ? ResourceKey.create(ModuleDefinition.REGISTRY, BuiltInRegistries.ITEM.getKey(stack.getItem()))
                : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("yafs.tooltip.module").withStyle(ChatFormatting.BLUE));

        ResourceKey<ModuleDefinition> kind = kind(stack);
        if (kind != null && context.registries() != null) {
            context.registries().lookup(ModuleDefinition.REGISTRY).flatMap(lookup -> lookup.get(kind)).ifPresent(holder -> {
                ModuleDefinition module = holder.value();
                if (module.speedMultiplier() > 1D) gain(tooltip, "yafs.module.speed", times(module.speedMultiplier()));
                if (module.extraPlants() > 0) gain(tooltip, "yafs.module.density", module.extraPlants());
                if (module.energyCapacity() > 0) gain(tooltip, "yafs.module.energy_capacity", String.format(java.util.Locale.ROOT, "%,d", module.energyCapacity()).replace(',', ' '));
                if (module.lightFloor() > 0) gain(tooltip, "yafs.module.light_floor", module.lightFloor());
                if (module.lightCap() < 15) gain(tooltip, "yafs.module.light_cap", module.lightCap());
                if (module.outputCapacity() > 0) gain(tooltip, "yafs.module.output_capacity", module.outputCapacity());
                if (module.composting() > 0) gain(tooltip, "yafs.module.composting", module.composting());
                if (module.temperatureShift() != 0) gain(tooltip, "yafs.module.temperature_shift", String.format("%+.0f", module.temperatureShift()));
                if (module.rainCollection() > 0) gain(tooltip, "yafs.module.rain_collection", module.rainCollection());
                if (module.energyMultiplier() > 1D) cost(tooltip, "yafs.module.energy_cost", times(module.energyMultiplier()));
                if (module.waterMultiplier() > 1D) cost(tooltip, "yafs.module.water_cost", times(module.waterMultiplier()));
                if (module.energyPerTick() > 0) cost(tooltip, "yafs.module.energy_per_tick", module.energyPerTick());

                if (module.extraPlants() > 0) cost(tooltip, "yafs.module.per_plant", module.extraPlants());
                if (module.speedMultiplier() > 1D || module.energyPerTick() > 0) {
                    tooltip.add(Component.translatable("yafs.tooltip.module.needs_energy").withStyle(ChatFormatting.DARK_GRAY));
                }
                tooltip.add(Component.translatable(module.maxStack() > 1 ? "yafs.tooltip.module.stack" : "yafs.tooltip.module.single", module.maxStack())
                        .withStyle(ChatFormatting.GRAY));
                if (module.lightFloor() > 0) tooltip.add(Component.translatable("yafs.tooltip.module.one_light").withStyle(ChatFormatting.GRAY));
                if (module.lightCap() < 15) tooltip.add(Component.translatable("yafs.tooltip.module.one_dark").withStyle(ChatFormatting.GRAY));
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
