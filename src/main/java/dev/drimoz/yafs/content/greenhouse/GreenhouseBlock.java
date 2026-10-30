package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.MapCodec;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
}
