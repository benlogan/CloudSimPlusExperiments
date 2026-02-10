package com.loganbe.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

import static com.loganbe.SimulationConfig.ACCEPTABLE_WORKLOAD_ERROR;

public class Deltas {

    public static final Logger LOGGER = LoggerFactory.getLogger(Deltas.class.getSimpleName());

    /**
     * MIPS Before/After Not Equal - incomplete work (or more than expected)
     * @param totalWorkExpected
     * @param actualAccumulatedMips
     * @return
     */
    //public double calculateWorkDelta(long totalWorkExpected, long totalWorkExpectedMax, BigInteger actualAccumulatedMips) {
    public static double calculateWorkDelta(long totalWorkExpected, BigInteger actualAccumulatedMips) {
        // acceptable error - 5 minute(s) of processing time (<5%)
        // long acceptableError = simSpec.getHostSpecification().getHost_mips() * simSpec.getHostSpecification().getHosts() * (5 * 60);
        // moving to pure percentage based approach (+ support for heterogeneous hardware)

        long deltaWork = BigInteger.valueOf(totalWorkExpected).subtract(actualAccumulatedMips).intValue();

        BigInteger expectedBig = BigInteger.valueOf(totalWorkExpected);
        BigInteger delta = expectedBig.subtract(actualAccumulatedMips).abs();

        //BigInteger expectedBigMax = BigInteger.valueOf(totalWorkExpectedMax);
        //BigInteger deltaMax = expectedBigMax.subtract(actualAccumulatedMips).abs();

        // convert to double for percentage calculation
        double deltaPercentage = delta.doubleValue() / totalWorkExpected * 100;
        //double deltaPercentageMax = deltaMax.doubleValue() / totalWorkExpectedMax * 100;

        if(deltaWork > 0 && deltaPercentage > ACCEPTABLE_WORKLOAD_ERROR) {
            LOGGER.warn(deltaWork + " (" + Maths.quickRound(deltaPercentage) + "%) = Unfinished MI");
        } else if(deltaWork < 0 && deltaPercentage > ACCEPTABLE_WORKLOAD_ERROR) {
            LOGGER.warn(Math.abs(deltaWork) + " = Excess Work (MI) = " + Maths.quickRound(deltaPercentage) + "%");
        }

        LOGGER.info(Maths.quickRound((100 - deltaPercentage)) + "% = Utilisation (using work complete)");
        //LOGGER.info((100 - deltaPercentageMax) + "% = Utilisation (using theoretical max)");

        return deltaPercentage;
    }

}