package dev.drimoz.yafs.content.multiblock;

import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;

import javax.annotation.Nullable;

public abstract class MultiblockBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private final MultiblockShape shape;

    protected MultiblockBlock(Properties properties, MultiblockShape shape) {
        super(properties);

        this.shape = shape;
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public MultiblockShape shape() {
        return this.shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected float getShadeBrightness(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();

        return canPlace(context, facing) ? defaultBlockState().setValue(FACING, facing) : null;
    }

    public boolean canPlace(BlockPlaceContext context, Direction facing) {
        Level level = context.getLevel();

        for (BlockPos pos : this.shape.positions(context.getClickedPos(), facing)) {
            if (level.isOutsideBuildHeight(pos)) return false;
            if (!level.getBlockState(pos).canBeReplaced(context)) return false;
            if (!level.isUnobstructed(null, Shapes.block().move(pos.getX(), pos.getY(), pos.getZ()))) return false;
        }
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        placeParts(level, pos, state.getValue(FACING));
    }

    public void placeParts(Level level, BlockPos master, Direction facing) {
        BlockState part = YafsBlocks.MULTIBLOCK_PART.get().defaultBlockState();

        for (BlockPos pos : this.shape.positions(master, facing)) {
            if (pos.equals(master)) continue;

            level.setBlock(pos, part.setValue(MultiblockPartBlock.BASE, pos.getY() == master.getY()), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity partEntity) {
                partEntity.setMaster(master);
            }
        }
    }

    @Nullable
    public <T> T partCapability(BlockCapability<T, Direction> capability, Level level, BlockEntity master, BlockState part, @Nullable Direction side) {
        return level.getCapability(capability, master.getBlockPos(), side);
    }

    public boolean growsInto(MultiblockBlock bigger) {
        return false;
    }

    public boolean canGrowInto(Level level, BlockPos master, Direction facing, MultiblockShape bigger) {
        for (BlockPos pos : bigger.positions(master, facing)) {
            if (pos.equals(master)) continue;
            if (level.isOutsideBuildHeight(pos)) return false;
            if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part && master.equals(part.masterPos())) continue;
            if (!level.getBlockState(pos).canBeReplaced()) return false;
            if (!level.isUnobstructed(null, Shapes.block().move(pos.getX(), pos.getY(), pos.getZ()))) return false;
        }
        return true;
    }

    public void detachParts(Level level, BlockPos master, Direction facing) {
        for (BlockPos pos : this.shape.positions(master, facing)) {
            if (pos.equals(master)) continue;
            if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part && master.equals(part.masterPos())) {
                part.detach();
                level.removeBlock(pos, false);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            for (BlockPos partPos : this.shape.positions(pos, state.getValue(FACING))) {
                if (partPos.equals(pos) || !level.isLoaded(partPos)) continue;

                if (level.getBlockEntity(partPos) instanceof MultiblockPartBlockEntity part
                        && pos.equals(part.masterPos())) {
                    level.removeBlock(partPos, false);
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public int bestSignal(Level level, BlockPos master) {
        BlockState state = level.getBlockState(master);
        if (!state.is(this)) return level.getBestNeighborSignal(master);

        int best = 0;
        for (BlockPos position : this.shape.positions(master, state.getValue(FACING))) {
            best = Math.max(best, level.getBestNeighborSignal(position));
            if (best >= 15) break;
        }
        return best;
    }
}
