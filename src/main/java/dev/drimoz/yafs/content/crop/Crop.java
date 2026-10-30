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
 * @param harvest      le bloc dont la table de loot donne la récolte, {@code null} pour la culture mûre
 * @param cost         ce que coûte un cycle : durée, eau, énergie ; chaque parcelle paie le sien, la serre la somme
 */
public record Crop(Item seed, Block block, IntegerProperty age, Set<Item> substrates, Requirements requirements,
                   @Nullable Item catalyst, @Nullable Block harvest, Cost cost) {

    /**
     * @param ticks  la durée d'un cycle à vitesse ×1 et efficacité pleine
     * @param water  l'eau d'un cycle complet, en mB
     * @param energy ce que coûte la parcelle alimentée, en FE/t, avant les modules
     */
    public record Cost(int ticks, int water, int energy) {

        /**
         * Le blé vanilla dans les meilleures conditions : 7 stades, une chance sur 3 d'avancer par
         * random tick sur terre labourée irriguée, un random tick tous les 4096/3 ticks en moyenne.
         * 7 × 3 × 4096 / 3 = 28 672 ticks, soit 24 minutes.
         */
        public static final Cost DEFAULT = new Cost(28_672, 500, 10);
    }

    public int maxAge() {
        return this.age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** La culture à son dernier âge. */
    public BlockState mature() {
        return this.block.defaultBlockState().setValue(this.age, maxAge());
    }

    /** L'état dont la table de loot donne la récolte : le fruit d'une tige, sinon la culture mûre. */
    public BlockState harvestState() {
        return this.harvest != null ? this.harvest.defaultBlockState() : mature();
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
