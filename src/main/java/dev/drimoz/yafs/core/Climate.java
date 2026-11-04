package dev.drimoz.yafs.core;

public final class Climate {

    private static final double PLAINS = 0.8D;
    private static final double PLAINS_CELSIUS = 20D;
    private static final double CELSIUS_PER_UNIT = (40D - PLAINS_CELSIUS) / (2.0D - PLAINS);

    public static double celsius(float biomeTemperature) {
        return PLAINS_CELSIUS + (biomeTemperature - PLAINS) * CELSIUS_PER_UNIT;
    }

    private Climate() {}
}
