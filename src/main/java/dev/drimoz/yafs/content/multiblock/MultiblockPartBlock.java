package dev.drimoz.yafs.content.multiblock;

import com.mojang.serialization.MapCodec;
import dev.drimoz.yafs.content.greenhouse.PortMode;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/**
 * Toute position d'un multibloc qui n'est pas le maître. Un seul bloc pour tous les multiblocs :
 * c'est son block entity qui sait à quel maître il appartient.
 *
 * <p>Invisible (le maître dessine tout le volume), sans item ni loot. Casser une partie casse le
 * maître, qui emporte les autres parties avec lui.
 */
public class MultiblockPartBlock extends BaseEntityBlock {

    public static final MapCodec<MultiblockPartBlock> CODEC = simpleCodec(MultiblockPartBlock::new);

    /** Un port de serre (YAF-050) : dans l'état du bloc, pour que le client le voie sans synchronisation. */
    public static final EnumProperty<PortMode> PORT = EnumProperty.create("port", PortMode.class);

    /** La couche du bas : un bloc plein, branchable de partout. Dans l'état, pour que le client la dessine. */
    public static final BooleanProperty BASE = BooleanProperty.create("base");

    public MultiblockPartBlock(Properties properties) {
        super(properties);

        registerDefaultState(this.stateDefinition.any().setValue(PORT, PortMode.NONE).setValue(BASE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PORT, BASE);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Invisible au-dessus du socle, sauf un port ; le socle est un bloc plein. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PORT).isPort() || state.getValue(BASE) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartBlockEntity(pos, state);
    }

    /**
     * Pas d'ombre ambiante : une partie a la collision d'un bloc plein, et le jeu en déduit qu'elle
     * assombrit ses voisins. Le socle entier sortait gris foncé au lieu de gris clair.
     */
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    // Interface (Casse)

    /** En créatif, le maître part sans rien lâcher, comme s'il avait été cassé lui-même. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos master = masterOf(level, pos);
            if (master != null) level.destroyBlock(master, false, player);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Quelle que soit la cause — joueur, explosion, {@code /setblock} — une partie qui disparaît
     * emporte le maître, qui lâche l'item et son contenu.
     *
     * <p>Appelé <i>avant</i> le retrait du block entity : on peut encore lire le maître. Quand
     * c'est le maître qui retire ses parties, il n'est déjà plus en place et rien ne se relance.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            // Un port rend son item, sauf détaché pour une montée de palier, qui le rend au joueur.
            if (state.getValue(PORT).isPort() && level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part && part.masterPos() != null) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(YafsItems.PORT.get()));
            }

            BlockPos master = masterOf(level, pos);
            if (master != null) level.destroyBlock(master, true);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // Interface (Voisinage)

    /** Redstone, inventaire posé à côté : c'est au maître d'en juger. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

        BlockPos master = masterOf(level, pos);
        if (master != null) level.getBlockState(master).handleNeighborChanged(level, master, neighborBlock, neighborPos, movedByPiston);
    }

    /** Un clic sur n'importe quelle partie vaut un clic sur le maître. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos master = knownMaster(level, pos);
        if (master == null) return InteractionResult.PASS;

        return level.getBlockState(master).useWithoutItem(level, player, hitResult.withPosition(master));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos master = knownMaster(level, pos);
        if (master == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        return level.getBlockState(master).useItemOn(stack, level, player, hand, hitResult.withPosition(master));
    }

    /** Pick block sur une partie : l'item du maître. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        BlockPos master = level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part ? part.masterPos() : null;
        if (master == null) return ItemStack.EMPTY;

        return level.getBlockState(master).getCloneItemStack(target, level, master, player);
    }

    // Inner work

    /** Le maître de cette partie, s'il est chargé et toujours un multibloc. */
    @Nullable
    private static BlockPos masterOf(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part)) return null;

        return part.master() != null ? part.masterPos() : null;
    }

    /** Valable côté client aussi : le block entity de la partie y connaît son maître (getUpdateTag). */
    @Nullable
    private static BlockPos knownMaster(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part ? part.masterPos() : null;
    }
}
