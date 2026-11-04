package dev.drimoz.yafs.content.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockShapeTest {

    private static final BlockPos MASTER = new BlockPos(10, 64, -3);

    @Test
    void aFirstTierGreenhouseIsCentredOnItsMaster() {
        MultiblockShape shape = new MultiblockShape(3, 3, 3);
        List<BlockPos> positions = shape.positions(MASTER, Direction.NORTH);

        assertEquals(27, new HashSet<>(positions).size());
        assertTrue(positions.contains(MASTER));
        assertTrue(positions.stream().allMatch(pos -> pos.getY() >= MASTER.getY()));

        AABB bounds = shape.bounds(MASTER, Direction.NORTH);
        assertEquals(new AABB(9, 64, -4, 12, 67, -1), bounds);
        assertTrue(positions.stream().allMatch(pos -> bounds.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
    }

    @Test
    void aNonSquareFootprintTurnsWithItsFacing() {
        MultiblockShape shape = new MultiblockShape(5, 1, 1);

        assertEquals(new AABB(8, 64, -3, 13, 65, -2), shape.bounds(MASTER, Direction.SOUTH));
        assertEquals(new AABB(10, 64, -5, 11, 65, 0), shape.bounds(MASTER, Direction.EAST));
    }

    @Test
    void anEvenFootprintHasNoCentreAndIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new MultiblockShape(2, 2, 3));
    }
}
