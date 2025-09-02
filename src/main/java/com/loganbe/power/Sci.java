package com.loganbe.power;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Software Carbon Intensity (SCI) score, from the Green Software Foundation (GSF);
 * https://sci.greensoftware.foundation/
 */
public class Sci {

    public static final Logger LOGGER = LoggerFactory.getLogger(Sci.class.getSimpleName());

    public static double calculateSci(double operationalEmissions, double emboddiedEmissions, double rate) {
        /*
        LOGGER.info("Calculating GSF SCI score...");
        LOGGER.info("operationalEmissions = " + operationalEmissions);
        LOGGER.info("emboddiedEmissions = " + emboddiedEmissions);
        LOGGER.info("rate = " + rate);
         */
        return (operationalEmissions + emboddiedEmissions) / rate;
    }

}