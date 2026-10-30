package dev.drimoz.yafs.core;

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
    public record Requirements(int minLight, double minTemperature, double maxTemperature) {

        /** Ce qu'on suppose d'une culture dont on ne sait rien : lumière vanilla, large plage. */
        public static final Requirements TOLERANT = new Requirements(9, 0D, 35D);

        public Requirements {
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
        return falloff(requirements.minLight() - light, LIGHT_SPAN);
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
