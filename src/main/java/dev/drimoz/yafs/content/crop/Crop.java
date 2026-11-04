package dev.drimoz.yafs.content.crop;

import dev.drimoz.yafs.core.Efficiency.Requirements;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;
import java.util.Set;

public record Crop(Item seed, Block block, IntegerProperty age, Set<Item> substrates, Requirements requirements,
                   @Nullable ItemMatch catalyst, @Nullable Block harvest, Cost cost) {

    public record Cost(int ticks, int water, int energy, double residue, @Nullable Item residueItem) {

        public Cost(int ticks, int water, int energy) {
            this(ticks, water, energy, 0.2D, null);
        }

        public static final Cost DEFAULT = new Cost(28_672, 500, 10);
    }

    public int maxAge() {
        return this.age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public BlockState mature() {
        return this.block.defaultBlockState().setValue(this.age, maxAge());
    }

    public BlockState harvestState() {
        return this.harvest != null ? this.harvest.defaultBlockState() : mature();
    }

    public BlockState stateAt(float progress) {
        return this.block.defaultBlockState().setValue(this.age, (int) (progress * maxAge()));
    }

    public boolean accepts(Item substrate) {
        return this.substrates.contains(substrate);
    }

    public boolean catalysedBy(Item catalyst) {
        return this.catalyst == null || this.catalyst.contains(catalyst);
    }
}
