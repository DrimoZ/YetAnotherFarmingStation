package dev.drimoz.yafs.content.crop;

import dev.drimoz.yafs.core.Efficiency.Requirements;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Une culture que la serre sait faire pousser : la graine, le bloc qu'elle pose, son âge, et les
 * substrats qu'elle accepte.
 *
 * @param age        la propriété d'âge du bloc ; son maximum est l'état mûr
 * @param substrates   les items de substrat acceptés, tels que la détection les a trouvés
 * @param requirements ce que la culture attend de l'environnement
 * @param catalyst     le bloc à poser sous le substrat, {@code null} si la culture n'en veut pas
 */
public record Crop(Item seed, Block block, IntegerProperty age, Set<Item> substrates, Requirements requirements,
                   @Nullable Item catalyst) {

    public int maxAge() {
        return this.age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** L'état dont la table de loot donne la récolte. */
    public BlockState mature() {
        return this.block.defaultBlockState().setValue(this.age, maxAge());
    }

    /** L'état à dessiner pour une progression de 0 à 1. */
    public BlockState stateAt(float progress) {
        return this.block.defaultBlockState().setValue(this.age, (int) (progress * maxAge()));
    }

    public boolean accepts(Item substrate) {
        return this.substrates.contains(substrate);
    }

    /** Sans exigence, n'importe quoi convient, même rien. */
    public boolean catalysedBy(Item catalyst) {
        return this.catalyst == null || this.catalyst == catalyst;
    }
}
