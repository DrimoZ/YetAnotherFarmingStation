package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.MapCodec;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockShape;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Le maître d'une serre. Un palier, un volume : P1 seulement pour l'instant, les autres arrivent
 * avec YAF-005.
 */
public class GreenhouseBlock extends MultiblockBlock {

    public static final MultiblockShape TIER_1 = new MultiblockShape(3, 3, 3);

    public static final MapCodec<GreenhouseBlock> CODEC = simpleCodec(GreenhouseBlock::new);

    public GreenhouseBlock(Properties properties) {
        super(properties, TIER_1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GreenhouseBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, YafsBlocks.GREENHOUSE_ENTITY.get(), (l, pos, s, greenhouse) -> greenhouse.serverTick());
    }

    /**
     * Clic avec une graine ou un bloc : posé dans la première parcelle qui l'attend. Les parties
     * renvoient leurs clics ici, donc n'importe quel bloc de la serre convient.
     */
    // ponytail: interaction provisoire pour voir pousser en jeu, remplacée par l'écran (YAF-031, YAF-032).
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof BlockItem item) || stack.is(asItem())
                || !(level.getBlockEntity(pos) instanceof GreenhouseBlockEntity greenhouse)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        int slot = Crops.of(item) != null ? GreenhouseBlockEntity.CROP : GreenhouseBlockEntity.SUBSTRATE;
        for (int plot = 0; plot < GreenhouseBlockEntity.PLOTS; plot++) {
            if (greenhouse.plots().getStackInSlot(plot * GreenhouseBlockEntity.SLOTS_PER_PLOT + slot).isEmpty()) {
                greenhouse.setPlotItem(plot, slot, stack.copyWithCount(1));
                stack.consume(1, player);
                return ItemInteractionResult.CONSUME;
            }
        }
        return ItemInteractionResult.FAIL;
    }

    /** Main vide, ou un item qui ne va pas en parcelle : l'écran. Les parties renvoient leurs clics ici. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof GreenhouseBlockEntity greenhouse)) return InteractionResult.PASS;

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GreenhouseMenu(id, inventory, greenhouse), getName()),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Le contenu tombe avant que le block entity ne disparaisse ; le loot, lui, ne porte que l'item. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GreenhouseBlockEntity greenhouse) {
            greenhouse.dropContents(level, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
