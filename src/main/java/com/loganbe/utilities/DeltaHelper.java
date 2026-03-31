package com.loganbe.utilities;

import com.loganbe.templates.SimSpecFromFileLegacy;
import org.cloudsimplus.core.CloudSimPlus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Map;

import static com.loganbe.SimulationConfig.ACCEPTABLE_WORKLOAD_ERROR;

public class DeltaHelper {

    public static final Logger LOGGER = LoggerFactory.getLogger(DeltaHelper.class.getSimpleName());

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

    /**
     * when running multiple simulations, calculate some basic deltas and log results
     */
    public static void calculateDeltas(Map<Integer, Double> energyMap, Map<Integer, Double> workMap) {
        for(int i = 0; i < energyMap.size(); i++) {
            LOGGER.info("Sim Run : {} energy : {}Wh", i, energyMap.get(i+1));
            LOGGER.info("Sim Run : {} work : {}(MIPS)", i, workMap.get(i+1));
        }
        double energyDelta = energyMap.get(1) - energyMap.get(2);
        LOGGER.info("Energy Delta, when applying interventions during a 2nd sim run : {} Wh (or {} less energy consumed)",
                String.format("%.2f", energyDelta), String.format("%.2f %%", (energyDelta / energyMap.get(1))*100));

        double workDelta = workMap.get(1) - workMap.get(2);
        LOGGER.info("Work Delta, when applying interventions during a 2nd sim run : {} MIPS (or {} less work done)",
                String.format("%.2f", workDelta), String.format("%.2f %%", (workDelta / workMap.get(1))*100));
    }

    public static double calculateTimeDelta(SimSpecFromFileLegacy simSpec, CloudSimPlus simulation, long totalWorkExpected) {
        //double expectedCompletionTimeS = (simSpec.SIM_TOTAL_WORK / (simSpec.HOST_PES * simSpec.HOST_MIPS)); // doesn't matter how many cores the host has, we can only use 1 host at a time (with space scheduler)
        double expectedCompletionTimeS = (totalWorkExpected / (simSpec.getHostSpecification().getHosts() * simSpec.getHostSpecification().getHost_mips()));
        LOGGER.debug("Expected Completion Time " + (expectedCompletionTimeS / 60 / 60) + "hr(s)");
        LOGGER.debug("Actual Completion Time " + simulation.clockInHours() + "hr(s)");
        double timeDelta = simulation.clock() - expectedCompletionTimeS;
        if (timeDelta > 100) { // a small tolerance for error
            LOGGER.warn("Gap in expected completion time (assuming full utilisation) = " + timeDelta + "s");
        }
        return timeDelta;
    }

}