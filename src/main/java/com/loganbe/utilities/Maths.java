package com.loganbe.utilities;

import java.util.SplittableRandom;

/*
 * helper utilities for maths & stats (e.g. randomness)
 * FIXME - properly document this later
 */
public class Maths {

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

}