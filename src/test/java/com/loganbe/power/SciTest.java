package com.loganbe.power;

import junit.framework.TestCase;
import org.junit.Assert;

public class SciTest extends TestCase {

    public void testCalculateSci() {
        double operationalEmissions = 55.55;
        double embodiedEmissions = 444.44;
        double rate = 22;
        double result = Sci.calculateSci(operationalEmissions, embodiedEmissions, rate, true);
        Assert.assertEquals(22726.8182, result, 0);
    }

}