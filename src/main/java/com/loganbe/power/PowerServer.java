package com.loganbe.power;

/**
 * simply for storing the results of a servers power/energy calculation
 */
public class PowerServer {

    private final double utilizationPercentMean;
    private final double watts;

    public PowerServer(double utilizationPercentMean, double watts) {
        this.utilizationPercentMean = utilizationPercentMean;
        this.watts = watts;
    }

    public double getUtilizationPercentMean() {
        return utilizationPercentMean;
    }

    public double getWatts() {
        return watts;
    }

}