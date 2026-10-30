package dev.drimoz.yafs.core;

/**
 * L'état d'une parcelle, dans les cinq statuts communs aux écrans (voir {@code GuiTheme.Status}
 * de FactoryIO). Une serre prend le plus grave de ses parcelles : l'ordre de déclaration va du
 * moins au plus grave.
 */
public enum PlotStatus {
    /** Éteinte, par la redstone ou l'interrupteur. */
    OFF,
    /** Rien à faire : parcelle vide. */
    WAITING,
    WORKING,
    /** Mûre, mais la sortie est pleine : rien n'est perdu, elle attend. */
    BLOCKED,
    /** Ne poussera pas sans intervention : culture inconnue, substrat incompatible, eau à zéro. */
    PROBLEM
}
