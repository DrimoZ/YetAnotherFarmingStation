package dev.drimoz.yafs.core;

import net.minecraft.util.Mth;

public record RedstoneCondition(Mode mode, int threshold) {

    public static final int MAX_SIGNAL = 15;

    public static final RedstoneCondition DEFAULT = new RedstoneCondition(Mode.BELOW, 1);

    public RedstoneCondition {
        threshold = Mth.clamp(threshold, 0, MAX_SIGNAL);
    }

    public enum Mode {

        ALWAYS,

        BELOW,

        AT_LEAST;

        private static final Mode[] VALUES = values();

        public static Mode byOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal >= VALUES.length) return BELOW;

            return VALUES[ordinal];
        }

        public Mode next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        public String translationKey() {
            return "redstone_" + name().toLowerCase();
        }
    }

    public boolean allows(int signal) {
        return switch (mode) {
            case ALWAYS -> true;
            case BELOW -> signal < threshold;
            case AT_LEAST -> signal >= threshold;
        };
    }

    public boolean usesThreshold() {
        return mode != Mode.ALWAYS;
    }

    public RedstoneCondition withMode(Mode mode) {
        return new RedstoneCondition(mode, threshold);
    }

    public RedstoneCondition withThreshold(int threshold) {
        return new RedstoneCondition(mode, threshold);
    }

    public RedstoneCondition nextThreshold() {
        return withThreshold(threshold >= MAX_SIGNAL ? 0 : threshold + 1);
    }
}
