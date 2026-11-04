package dev.drimoz.yafs.client.greenhouse;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.crop.SubstrateDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = YetAnotherFarmingStation.MOD_ID, value = Dist.CLIENT)
public final class SoilTooltips {

    private SoilTooltips() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        event.getToolTip().addAll(lines(event.getItemStack().getItem()));
    }

    public static List<Component> lines(Item substrate) {
        SubstrateDefinition.Bonus bonus = Crops.bonus(substrate);
        List<Component> lines = new ArrayList<>(2);
        if (bonus.yield() > 1D) {
            lines.add(Component.translatable("yafs.tooltip.soil.yield", GreenhouseScreen.number(bonus.yield())).withStyle(ChatFormatting.GREEN));
        }
        if (bonus.speed() > 1D) {
            lines.add(Component.translatable("yafs.tooltip.soil.speed", GreenhouseScreen.number(bonus.speed())).withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }
}
