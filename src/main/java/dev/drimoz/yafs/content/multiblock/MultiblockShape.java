package dev.drimoz.yafs.content.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public record MultiblockShape(int width, int height, int depth) {

    public MultiblockShape {
        if (width < 1 || height < 1 || depth < 1 || width % 2 == 0 || depth % 2 == 0) {
            throw new IllegalArgumentException(
                    "Volume de multibloc invalide : " + width + "×" + height + "×" + depth);
        }
    }

    public List<BlockPos> positions(BlockPos master, Direction facing) {
        int halfX = halfX(facing);
        int halfZ = halfZ(facing);

        List<BlockPos> positions = new ArrayList<>((2 * halfX + 1) * this.height * (2 * halfZ + 1));
        for (int dy = 0; dy < this.height; dy++) {
            for (int dx = -halfX; dx <= halfX; dx++) {
                for (int dz = -halfZ; dz <= halfZ; dz++) {
                    positions.add(master.offset(dx, dy, dz));
                }
            }
        }
        return positions;
    }

    public AABB bounds(BlockPos master, Direction facing) {
        int halfX = halfX(facing);
        int halfZ = halfZ(facing);

        return new AABB(
                master.getX() - halfX, master.getY(), master.getZ() - halfZ,
                master.getX() + halfX + 1, master.getY() + this.height, master.getZ() + halfZ + 1);
    }

    private int halfX(Direction facing) {
        return (facing.getAxis() == Direction.Axis.Z ? this.width : this.depth) / 2;
    }

    private int halfZ(Direction facing) {
        return (facing.getAxis() == Direction.Axis.Z ? this.depth : this.width) / 2;
    }
}
