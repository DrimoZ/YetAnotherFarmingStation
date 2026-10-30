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

import javax.annotation.Nullable;

/**
 * Le bloc <b>maître</b> d'un multibloc : celui que pose l'item, au centre-bas du volume.
 *
 * <p>Il porte toute la logique ; les autres positions sont des {@link MultiblockPartBlock} qui
 * ne font que lui renvoyer ce qu'on leur demande. Porté de FactoryIO ({@code docs/12} §2) ; la
 * base {@code ModEntityBlock} de FactoryIO n'a pas suivi, seule l'orientation en venait.
 *
 * <p>Les sous-classes déclarent {@code PushReaction.BLOCK} dans leurs propriétés : un piston
 * qui déplacerait le seul maître laisserait ses parties orphelines.
 */
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

    /** {@code BaseEntityBlock} rend invisible par défaut ; le maître a un modèle. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // Interface (Pose)

    /** La façade regarde le joueur. {@code null} annule la pose : c'est ainsi que vanilla refuse un bloc. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();

        return canPlace(context, facing) ? defaultBlockState().setValue(FACING, facing) : null;
    }

    /**
     * Toutes les positions du volume sont remplaçables et libres d'entité.
     *
     * <p>Le maître lui-même est déjà vérifié par {@code BlockItem} ; le reprendre ne coûte rien
     * et garde le contour de pose d'accord avec la pose réelle.
     */
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

    /** Pose les parties autour d'un maître déjà en place. */
    public void placeParts(Level level, BlockPos master, Direction facing) {
        BlockState part = YafsBlocks.MULTIBLOCK_PART.get().defaultBlockState();

        for (BlockPos pos : this.shape.positions(master, facing)) {
            if (pos.equals(master)) continue;

            level.setBlock(pos, part, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity partEntity) {
                partEntity.setMaster(master);
            }
        }
    }

    // Interface (Agrandissement)

    /** {@code bigger} peut-il être posé par-dessus ce multibloc pour l'agrandir ? Non, par défaut. */
    public boolean growsInto(MultiblockBlock bigger) {
        return false;
    }

    /**
     * Ce volume plus grand tient-il autour de ce maître ? Ses propres parties ne gênent pas ; le
     * reste doit être remplaçable et libre d'entité, comme à la pose.
     */
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

    /**
     * Retire les parties sans casser le maître. Chaque partie est détachée d'abord : sinon, en
     * disparaissant, elle croirait le volume cassé et emporterait le maître avec elle.
     */
    public void detachParts(Level level, BlockPos master, Direction facing) {
        for (BlockPos pos : this.shape.positions(master, facing)) {
            if (pos.equals(master)) continue;
            if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part && master.equals(part.masterPos())) {
                part.detach();
                level.removeBlock(pos, false);
            }
        }
    }

    // Interface (Casse)

    /**
     * Le maître parti, ses parties le suivent — sans rien lâcher : l'item et le contenu sortent
     * du maître seul.
     *
     * <p>Une partie dans un chunk non chargé est laissée en place plutôt que de forcer son
     * chargement ; orpheline, elle ne renvoie plus rien.
     */
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

    // Interface (Redstone)

    /** Le plus fort signal reçu par l'une quelconque des positions du volume, de 0 à 15. */
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
