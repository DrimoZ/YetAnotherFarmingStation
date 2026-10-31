package dev.drimoz.yafs.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * La loi du minimum : chaque facteur d'environnement donne une efficacité, seule la pire compte.
 * Voir {@code docs/01-DESIGN.md} §4.3.
 *
 * <p>Dans la plage idéale, 1. Hors de la plage, une baisse linéaire avec l'écart, jamais sous
 * {@link #FLOOR} : un écart ralentit, il ne bloque pas. Seule exception, l'eau à zéro, qui est une
 * ressource qui manque et non un écart.
 */
public final class Efficiency {

    public static final double FLOOR = 0.1D;

    /** Niveaux de lumière sous le minimum qui font tomber au plancher. */
    static final double LIGHT_SPAN = 8D;
    /** Degrés hors plage qui font tomber au plancher. */
    static final double TEMPERATURE_SPAN = 15D;

    public enum Factor { LIGHT, WATER, TEMPERATURE }

    /** Ce qu'une culture attend. */
    public record Requirements(int minLight, int maxLight, double minTemperature, double maxTemperature) {

        /** Sans plafond de lumière : celui qu'on suppose presque partout. */
        public Requirements(int minLight, double minTemperature, double maxTemperature) {
            this(minLight, 15, minTemperature, maxTemperature);
        }

        /** Ce qu'on suppose d'une culture dont on ne sait rien : lumière vanilla, large plage. */
        public static final Requirements TOLERANT = new Requirements(9, 15, 0D, 35D);

        /**
         * Borné ; une plage inversée est refusée en {@code DataResult}, pas levée ni retournée. Les
         * champs absents prennent la valeur tolérante. Lu en trois nombres d'abord, pour que le
         * constructeur, qui lève, ne voie que des plages déjà vérifiées.
         */
        public static final Codec<Requirements> CODEC = RecordCodecBuilder.<double[]>create(instance -> instance.group(
                Codec.intRange(0, 15).optionalFieldOf("min_light", TOLERANT.minLight()).forGetter(raw -> (int) raw[0]),
                Codec.intRange(0, 15).optionalFieldOf("max_light", TOLERANT.maxLight()).forGetter(raw -> (int) raw[1]),
                Codec.doubleRange(-100D, 100D).optionalFieldOf("min_temperature", TOLERANT.minTemperature()).forGetter(raw -> raw[2]),
                Codec.doubleRange(-100D, 100D).optionalFieldOf("max_temperature", TOLERANT.maxTemperature()).forGetter(raw -> raw[3])
        ).apply(instance, (minLight, maxLight, min, max) -> new double[]{minLight, maxLight, min, max})).flatXmap(
                raw -> raw[2] > raw[3]
                        ? DataResult.error(() -> "Plage de température inversée : " + raw[2] + " > " + raw[3])
                        : raw[0] > raw[1]
                        ? DataResult.error(() -> "Plage de lumière inversée : " + (int) raw[0] + " > " + (int) raw[1])
                        : DataResult.success(new Requirements((int) raw[0], (int) raw[1], raw[2], raw[3])),
                requirements -> DataResult.success(new double[]{requirements.minLight(), requirements.maxLight(), requirements.minTemperature(), requirements.maxTemperature()}));

        public Requirements {
            if (minLight > maxLight) throw new IllegalArgumentException("Plage de lumière inversée : " + minLight + " > " + maxLight);
            if (minTemperature > maxTemperature) {
                throw new IllegalArgumentException("Plage de température inversée : " + minTemperature + " > " + maxTemperature);
            }
        }
    }

    /**
     * Ce que fournit la serre.
     *
     * @param water la part de la consommation que la réserve couvre, de 0 (vide) à 1
     */
    public record Conditions(int light, double temperature, double water) {}

    /** L'efficacité retenue, et le facteur qui l'impose. */
    public record Verdict(double value, Factor limiting) {

        public boolean isIdeal() {
            return this.value >= 1D;
        }
    }

    public static Verdict evaluate(Requirements requirements, Conditions conditions) {
        double light = light(requirements, conditions.light());
        double water = water(conditions.water());
        double temperature = temperature(requirements, conditions.temperature());

        // À égalité, l'ordre lumière, eau, température : le premier est le plus simple à corriger.
        if (light <= water && light <= temperature) return new Verdict(light, Factor.LIGHT);
        if (water <= temperature) return new Verdict(water, Factor.WATER);
        return new Verdict(temperature, Factor.TEMPERATURE);
    }

    static double light(Requirements requirements, int light) {
        return falloff(Math.max(requirements.minLight() - light, light - requirements.maxLight()), LIGHT_SPAN);
    }

    static double water(double coverage) {
        if (coverage <= 0D) return 0D;
        return Math.max(FLOOR, Math.min(1D, coverage));
    }

    static double temperature(Requirements requirements, double temperature) {
        double gap = Math.max(requirements.minTemperature() - temperature, temperature - requirements.maxTemperature());
        return falloff(gap, TEMPERATURE_SPAN);
    }

    private static double falloff(double gap, double span) {
        if (gap <= 0D) return 1D;
        return Math.max(FLOOR, 1D - gap / span);
    }

    private Efficiency() {}
}
