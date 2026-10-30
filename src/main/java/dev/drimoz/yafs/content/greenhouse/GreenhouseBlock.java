package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.MapCodec;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockShape;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
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

    /** Le contenu tombe avant que le block entity ne disparaisse ; le loot, lui, ne porte que l'item. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GreenhouseBlockEntity greenhouse) {
            greenhouse.dropContents(level, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
