package dev.drimoz.yafs.content.debug;

import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class CreativeEnergySourceBlockEntity extends BlockEntity {

    public CreativeEnergySourceBlockEntity(BlockPos pos, BlockState state) {
        super(YafsBlocks.CREATIVE_ENERGY_SOURCE_ENTITY.get(), pos, state);
    }

    public void serverTick() {
        if (this.level == null) return;

        for (Direction side : Direction.values()) {
            IEnergyStorage target = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, this.worldPosition.relative(side), side.getOpposite());
            if (target != null && target.canReceive()) target.receiveEnergy(Integer.MAX_VALUE, false);
        }
    }
}
