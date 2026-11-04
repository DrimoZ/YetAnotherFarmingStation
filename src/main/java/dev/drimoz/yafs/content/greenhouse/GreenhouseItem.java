package dev.drimoz.yafs.content.greenhouse;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class GreenhouseItem extends BlockItem {

    private final GreenhouseTier tier;

    public GreenhouseItem(GreenhouseBlock block, Properties properties) {
        super(block, properties);
        this.tier = block.tier();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("yafs.tooltip.greenhouse.capacity", this.tier.plots(), this.tier.moduleSlots())
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("yafs.tooltip.greenhouse.volume",
                this.tier.shape().width(), this.tier.shape().height(), this.tier.shape().depth()).withStyle(ChatFormatting.GRAY));
        if (this.tier.ordinal() == 0) {
            tooltip.add(Component.translatable("yafs.tooltip.greenhouse.place").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("yafs.tooltip.greenhouse.upgrade", this.tier.ordinal()).withStyle(ChatFormatting.AQUA));
        }
    }
}
