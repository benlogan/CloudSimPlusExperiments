package com.loganbe.utilities;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class DeltasTest extends TestCase {

    @Test
    public void testCalculateWorkDelta() {

        // same result
        int expected = 100;
        BigInteger actual = BigInteger.valueOf(100);
        double deltaPercentage = Deltas.calculateWorkDelta(expected, actual);
        Assert.assertEquals(0, deltaPercentage, 0);

        // a little more
        expected = 1000;
        actual = BigInteger.valueOf(1010);
        deltaPercentage = Deltas.calculateWorkDelta(expected, actual);
        Assert.assertEquals(1, deltaPercentage, 0);

        // a little less
        expected = 1000;
        actual = BigInteger.valueOf(990);
        deltaPercentage = Deltas.calculateWorkDelta(expected, actual);
        Assert.assertEquals(1, deltaPercentage, 0);

        // a lot more
        expected = 1000;
        actual = BigInteger.valueOf(1100);
        deltaPercentage = Deltas.calculateWorkDelta(expected, actual);
        Assert.assertEquals(10, deltaPercentage, 0);

        // a lot less
        expected = 1000;
        actual = BigInteger.valueOf(900);
        deltaPercentage = Deltas.calculateWorkDelta(expected, actual);
        Assert.assertEquals(10, deltaPercentage, 0);
    }

}