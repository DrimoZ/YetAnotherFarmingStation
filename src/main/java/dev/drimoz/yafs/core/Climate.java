package dev.drimoz.yafs.core;

/**
 * La température vanilla d'un biome, sans unité, traduite en degrés pour l'écran et les exigences.
 *
 * <p>Droite calée sur deux repères : plaines (0,8) à 20 °C, désert (2,0) à 40 °C. Elle donne
 * environ 11 °C en taïga (0,25), 7 °C à 0 et −2 °C dans les biomes gelés (−0,5).
 */
public final class Climate {

    private static final double PLAINS = 0.8D;
    private static final double PLAINS_CELSIUS = 20D;
    private static final double CELSIUS_PER_UNIT = (40D - PLAINS_CELSIUS) / (2.0D - PLAINS);

    public static double celsius(float biomeTemperature) {
        return PLAINS_CELSIUS + (biomeTemperature - PLAINS) * CELSIUS_PER_UNIT;
    }

    private Climate() {}
}
