package dev.drimoz.yafs.content.multiblock;

import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Une partie de multibloc : elle ne sait qu'où est son maître.
 *
 * <p>Ses capabilities sont celles du maître, demandées au monde à la position du maître : un
 * tuyau ou un mod tiers ne voit qu'un inventaire ordinaire.
 */
public class MultiblockPartBlockEntity extends BlockEntity {

    private static final String MASTER_TAG = "master";

    /** Les capabilities qu'une partie relaie. */
    private static final List<BlockCapability<?, Direction>> RELAYED = List.of(
            Capabilities.ItemHandler.BLOCK,
            Capabilities.FluidHandler.BLOCK,
            Capabilities.EnergyStorage.BLOCK);

    /** Décalage vers le maître ; relatif, pour survivre à une structure déplacée. */
    private BlockPos masterOffset = BlockPos.ZERO;

    public MultiblockPartBlockEntity(BlockPos pos, BlockState state) {
        super(YafsBlocks.MULTIBLOCK_PART_ENTITY.get(), pos, state);
    }

    public void setMaster(BlockPos master) {
        this.masterOffset = master.subtract(this.worldPosition);
        setChanged();
    }

    /** Plus de maître : la partie peut disparaître sans rien emporter. */
    public void detach() {
        this.masterOffset = BlockPos.ZERO;
    }

    /** {@code null} tant qu'aucun maître n'a été désigné. */
    @Nullable
    public BlockPos masterPos() {
        return this.masterOffset.equals(BlockPos.ZERO) ? null : this.worldPosition.offset(this.masterOffset);
    }

    /**
     * Le maître, s'il est chargé et bien un multibloc.
     *
     * <p>Jamais de chargement forcé : {@code getBlockEntity} sur un chunk absent le chargerait,
     * et une capability demandée à la frontière suffirait à le déclencher.
     */
    @Nullable
    public BlockEntity master() {
        BlockPos master = masterPos();
        if (this.level == null || master == null || !this.level.isLoaded(master)) return null;
        if (!(this.level.getBlockState(master).getBlock() instanceof MultiblockBlock)) return null;

        return this.level.getBlockEntity(master);
    }

    // Capabilities

    /**
     * Ce qu'une partie expose, c'est son maître qui le décide ({@link MultiblockBlock#partCapability}) :
     * une serre n'ouvre que ses ports. Les handlers du maître ne changent pas d'objet ; quand le
     * port d'une partie change, celui qui le change invalide la position.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (BlockCapability<?, Direction> capability : RELAYED) {
            relay(event, capability);
        }
    }

    private static <T> void relay(RegisterCapabilitiesEvent event, BlockCapability<T, Direction> capability) {
        event.registerBlockEntity(capability, YafsBlocks.MULTIBLOCK_PART_ENTITY.get(), (part, side) -> {
            Level level = part.getLevel();
            BlockEntity master = part.master();
            if (level == null || master == null) return null;
            return ((MultiblockBlock) master.getBlockState().getBlock()).partCapability(capability, level, master, part.getBlockState(), side);
        });
    }

    // Persistance et synchronisation : le client en a besoin pour le pick block et les clics.

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(MASTER_TAG, NbtUtils.writeBlockPos(this.masterOffset));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.masterOffset = NbtUtils.readBlockPos(tag, MASTER_TAG).orElse(BlockPos.ZERO);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
