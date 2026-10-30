package dev.drimoz.yafs.content.greenhouse;

import dev.drimoz.yafs.content.multiblock.MultiblockPartBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * L'item Port (YAF-050) : utilisé sur un bloc d'une serre, il en fait un port de sortie.
 * Réutilisé sur un port, il passe au mode suivant ; accroupi, il le retire et revient en main.
 * Une serre n'a pas plus de ports que son palier ne le permet.
 */
public class PortItem extends Item {

    public PortItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("yafs.tooltip.port.place").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("yafs.tooltip.port.cycle").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("yafs.tooltip.port.remove").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("yafs.tooltip.port.limit").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        if (!state.is(YafsBlocks.MULTIBLOCK_PART.get()) || !(level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part)
                || part.masterPos() == null || !(level.getBlockState(part.masterPos()).getBlock() instanceof GreenhouseBlock greenhouse)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        PortMode mode = state.getValue(MultiblockPartBlock.PORT);
        PortMode chosen;
        if (mode.isPort() && player != null && player.isShiftKeyDown()) {
            chosen = PortMode.NONE;
            player.getInventory().placeItemBackInInventory(new ItemStack(this));
        } else if (mode.isPort()) {
            chosen = mode.next();
        } else if (greenhouse.portCount(level, part.masterPos()) >= greenhouse.tier().maxPorts()) {
            if (player != null) player.displayClientMessage(Component.translatable("yafs.port.limit", greenhouse.tier().maxPorts()), true);
            return InteractionResult.FAIL;
        } else {
            chosen = PortMode.OUTPUT_ALL;
            context.getItemInHand().consume(1, player);
        }

        level.setBlock(pos, state.setValue(MultiblockPartBlock.PORT, chosen), Block.UPDATE_ALL);
        level.invalidateCapabilities(pos);
        if (player != null) {
            player.displayClientMessage(Component.translatable("yafs.port.mode." + chosen.getSerializedName()), true);
        }
        return InteractionResult.CONSUME;
    }
}
