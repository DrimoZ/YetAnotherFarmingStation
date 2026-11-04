package dev.drimoz.yafs.core;

public enum PlotStatus {

    OFF,

    WAITING,
    WORKING,

    BLOCKED,

    PROBLEM;

    public static PlotStatus worst(Iterable<PlotStatus> statuses) {
        PlotStatus worst = OFF;
        for (PlotStatus status : statuses) {
            if (status.compareTo(worst) > 0) worst = status;
        }
        return worst;
    }
}
