package com.loganbe.power;

import com.loganbe.utilities.Maths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Software Carbon Intensity (SCI) score, from the Green Software Foundation (GSF);
 * https://sci.greensoftware.foundation/
 */
public class Sci {

    public static final Logger LOGGER = LoggerFactory.getLogger(Sci.class.getSimpleName());

    /**
     * calculate the ISO Standard GSF SCI score
     * @param operationalEmissions (gCO₂e)
     * @param embodiedEmissions (gCO₂e)
     * @param rate
     * @return SCI score (mgCO₂e / rate)
     */
    public static double calculateSci(double operationalEmissions, double embodiedEmissions, double rate, boolean log) {

        if(rate == 0) {
            //LOGGER.error("Can't calculate SCI with a zero rate! setting a tiny amount of work done!");
            return 1; // SCI doesn't handle zero work. fix the SCI at 1 - not a good score
            //rate = 0.000000001;
        }

        double sci = (operationalEmissions + embodiedEmissions) / rate;
        sci = Maths.scaleAndRound(sci * 1000); // convert to mg - more readable (and round/scale)

        if(log) {
            LOGGER.info("Calculating GSF SCI score...");
            LOGGER.info("operationalEmissions = " + operationalEmissions);
            LOGGER.info("embodiedEmissions = " + embodiedEmissions);
            LOGGER.info("rate = " + rate);
            LOGGER.info("SCI = " + sci);
        }

        return sci;
    }

    /**
     * amortize emissions to the normal simulation sampling window (0.1s)
     * @param emissions
     * @return
     */
    public static double amortizeEmbodiedToSample(double emissions) {
        return ((emissions / 4) / 8760) / 36_000; // per year, per hour, per sample
    }

    /**
     * amortize emissions to a 1 hour window
     * @param emissions
     * @return
     */
    public static double amortizeEmbodiedToHour(double emissions) {
        return (emissions / 4) / 8760; // per year, per hour
    }

}