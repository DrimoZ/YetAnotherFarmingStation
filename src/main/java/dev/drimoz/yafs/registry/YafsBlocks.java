package dev.drimoz.yafs.registry;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YafsBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(YetAnotherFarmingStation.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, YetAnotherFarmingStation.MOD_ID);

    /** Un seul bloc de partie pour tous les multiblocs, sans item ni loot. */
    public static final DeferredBlock<MultiblockPartBlock> MULTIBLOCK_PART = BLOCKS.register("multiblock_part",
            () -> new MultiblockPartBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredBlock<GreenhouseBlock> GREENHOUSE_T1 = BLOCKS.register("greenhouse_t1",
            () -> new GreenhouseBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    // build(null) : aucun type de data fixer, comme pour tout block entity moddé.
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockPartBlockEntity>> MULTIBLOCK_PART_ENTITY =
            BLOCK_ENTITIES.register("multiblock_part",
                    () -> BlockEntityType.Builder.of(MultiblockPartBlockEntity::new, MULTIBLOCK_PART.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GreenhouseBlockEntity>> GREENHOUSE_ENTITY =
            BLOCK_ENTITIES.register("greenhouse",
                    () -> BlockEntityType.Builder.of(GreenhouseBlockEntity::new, GREENHOUSE_T1.get()).build(null));

    private YafsBlocks() {}
}
