package com.loganbe.power;

/**
 * representing datacentre PUE score - power utilisation metric
 * Power Usage Effectiveness
 * this could constitute a major PhD and framework contribution, with more work
 */
public class Pue {

    // pue = Total Facility Energy / IT Equipment Energy (servers)
    // using the generally accepted hyper-scale average, for now
    public double pue = 1.2; // @ 1.2, ~17% is overhead

    /**
     * take the server energy and, using the pue score, calculate the DC energy overhead
     * @param serverEnergy
     * @return
     */
    public double incrementalEnergyOverhead(double serverEnergy) {
        // Overhead Energy = (PUE - 1) × Server Energy Usage
        return (pue - 1) * serverEnergy;
    }

}