package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlock;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.fluids.FluidUtil;

import javax.annotation.Nullable;

public class GreenhouseBlock extends MultiblockBlock {

    public static final MapCodec<GreenhouseBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            GreenhouseTier.CODEC.fieldOf("tier").forGetter(GreenhouseBlock::tier),
            propertiesCodec()
    ).apply(instance, GreenhouseBlock::new));

    private final GreenhouseTier tier;

    public GreenhouseBlock(GreenhouseTier tier, Properties properties) {
        super(properties, tier.shape());

        this.tier = tier;
    }

    public GreenhouseTier tier() {
        return this.tier;
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

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

        if (level.getBlockEntity(pos) instanceof GreenhouseBlockEntity greenhouse) greenhouse.forgetSignal();
    }

    @Nullable
    @Override
    public <T> T partCapability(BlockCapability<T, Direction> capability, Level level, BlockEntity master, BlockState part, @Nullable Direction side) {
        if (!(master instanceof GreenhouseBlockEntity greenhouse)) return null;

        Object handler = part.getValue(MultiblockPartBlock.BASE) ? greenhouse.base(capability) : null;
        @SuppressWarnings("unchecked")
        T typed = (T) handler;
        return typed;
    }

    @Nullable
    public GreenhouseBlock upgradeFrom(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof GreenhouseBlock next
                && growsInto(next) ? next : null;
    }

    @Override
    public boolean growsInto(MultiblockBlock bigger) {
        return bigger instanceof GreenhouseBlock next && next.tier == this.tier.next();
    }

    public boolean canUpgrade(Level level, BlockPos master, GreenhouseBlock next) {
        return canGrowInto(level, master, level.getBlockState(master).getValue(FACING), next.shape());
    }

    private void upgrade(Level level, BlockPos master, GreenhouseBlock next, GreenhouseBlockEntity old) {
        Direction facing = level.getBlockState(master).getValue(FACING);
        CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());

        old.startUpgrade();
        detachParts(level, master, facing);
        level.setBlock(master, next.defaultBlockState().setValue(FACING, facing), Block.UPDATE_ALL);

        if (level.getBlockEntity(master) instanceof GreenhouseBlockEntity grown) {
            grown.loadWithComponents(saved, level.registryAccess());
            grown.setChanged();
        }
        next.placeParts(level, master, facing);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof GreenhouseBlockEntity greenhouse)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        GreenhouseBlock next = upgradeFrom(stack);
        if (next != null) {
            if (!canUpgrade(level, pos, next)) return ItemInteractionResult.FAIL;
            if (!level.isClientSide) {
                upgrade(level, pos, next, greenhouse);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (FluidUtil.interactWithFluidHandler(player, hand, greenhouse.water())) return ItemInteractionResult.sidedSuccess(level.isClientSide);

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof GreenhouseBlockEntity greenhouse)) return InteractionResult.PASS;

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GreenhouseMenu(id, inventory, greenhouse), getName()),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeEnum(this.tier);
                    });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GreenhouseBlockEntity greenhouse
                && !greenhouse.isUpgrading()) {
            greenhouse.dropContents(level, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
