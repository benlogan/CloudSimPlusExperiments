package com.loganbe.utilities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.SplittableRandom;

/*
 * helper utilities for maths & stats (e.g. randomness)
 * FIXME - properly document this later
 */
public class Maths {

    public final static int SCALING_FACTOR = 1_000_000;

    public static long twoPointLong(SplittableRandom rng, double desiredMean, long min, long max) {
        if (min >= max) return min;
        double p = (desiredMean - min) / (double)(max - min);
        p = Math.max(0.0, Math.min(1.0, p));
        return rng.nextDouble() < p ? max : min;
    }

    public static double twoPointDouble(SplittableRandom rng, double desiredMean, double min, double max) {
        if (min >= max) return min;
        double p = (desiredMean - min) / (max - min);
        p = Math.max(0.0, Math.min(1.0, p));
        return rng.nextDouble() < p ? max : min;
    }

    public static double quickRound(double number) {
        number = Math.round(number * 100);

        return number / 100;
    }

    public static double scaleAndRound(double number) {
        return new BigDecimal(number).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

}