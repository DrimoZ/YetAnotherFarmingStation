package dev.drimoz.yafs.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public final class Efficiency {

    public static final double FLOOR = 0.1D;

    static final double LIGHT_SPAN = 8D;

    static final double TEMPERATURE_SPAN = 15D;

    public enum Factor { LIGHT, WATER, TEMPERATURE }

    public record Requirements(int minLight, int maxLight, double minTemperature, double maxTemperature) {

        public Requirements(int minLight, double minTemperature, double maxTemperature) {
            this(minLight, 15, minTemperature, maxTemperature);
        }

        public static final Requirements TOLERANT = new Requirements(9, 15, 0D, 35D);

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

    public record Conditions(int light, double temperature, double water) {}

    public record Verdict(double value, Factor limiting) {

        public boolean isIdeal() {
            return this.value >= 1D;
        }
    }

    public static Verdict evaluate(Requirements requirements, Conditions conditions) {
        double light = light(requirements, conditions.light());
        double water = water(conditions.water());
        double temperature = temperature(requirements, conditions.temperature());

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
