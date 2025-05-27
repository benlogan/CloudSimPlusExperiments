package com.loganbe;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class MainTest {

    @Test
    public void testCalculateWorkDelta() {

        // same result
        int expected = 100;
        BigInteger actual = BigInteger.valueOf(100);
        double deltaPercentage = new Main().calculateWorkDelta(expected, actual);
        Assert.assertEquals(0, deltaPercentage, 0);

        // a little more
        expected = 1000;
        actual = BigInteger.valueOf(1010);
        deltaPercentage = new Main().calculateWorkDelta(expected, actual);
        Assert.assertEquals(1, deltaPercentage, 0);

        // a little less
        expected = 1000;
        actual = BigInteger.valueOf(990);
        deltaPercentage = new Main().calculateWorkDelta(expected, actual);
        Assert.assertEquals(1, deltaPercentage, 0);

        // a lot more
        expected = 1000;
        actual = BigInteger.valueOf(1100);
        deltaPercentage = new Main().calculateWorkDelta(expected, actual);
        Assert.assertEquals(10, deltaPercentage, 0);

        // a lot less
        expected = 1000;
        actual = BigInteger.valueOf(900);
        deltaPercentage = new Main().calculateWorkDelta(expected, actual);
        Assert.assertEquals(10, deltaPercentage, 0);
    }

}