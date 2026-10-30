package dev.drimoz.yafs.content.greenhouse;

import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Toute la logique d'une serre vivra ici ; vide tant que les parcelles n'existent pas (YAF-012). */
public class GreenhouseBlockEntity extends BlockEntity {

    public GreenhouseBlockEntity(BlockPos pos, BlockState state) {
        super(YafsBlocks.GREENHOUSE_ENTITY.get(), pos, state);
    }
}
