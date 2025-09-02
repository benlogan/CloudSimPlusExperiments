package com.loganbe.power;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Software Carbon Intensity (SCI) score, from the Green Software Foundation (GSF);
 * https://sci.greensoftware.foundation/
 */
public class Sci {

    public static final Logger LOGGER = LoggerFactory.getLogger(Sci.class.getSimpleName());

    public static double calculateSci(double operationalEmissions, double embodiedEmissions, double rate) {

        // these are lifetime embodied emissions numbers (raw)
        // they need to be amortized - shred to the same time-frame as the operational emissions (currently 1hr)
        // assuming 4 years of operational lifetime...
        embodiedEmissions = (embodiedEmissions / 4) / 8760; // per hour

        double sci = (operationalEmissions + embodiedEmissions) / rate;
        /*
        LOGGER.info("Calculating GSF SCI score...");
        LOGGER.info("operationalEmissions = " + operationalEmissions);
        LOGGER.info("embodiedEmissions = " + embodiedEmissions);
        LOGGER.info("rate = " + rate);
        LOGGER.info("SCI = " + sci);
        */

        return sci * 1000; // convert to mg - more readable
    }

}