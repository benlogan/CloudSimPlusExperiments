package com.loganbe.power;

import junit.framework.TestCase;
import org.junit.Assert;

public class CarbonTest extends TestCase {

    public void testEnergyToCarbon() {
        double energy = 5;
        double result = Carbon.energyToCarbon(energy);
        Assert.assertEquals(0.2025, result, 0);
    }

}