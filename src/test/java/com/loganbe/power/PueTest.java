package com.loganbe.power;

import junit.framework.TestCase;
import org.junit.Assert;

public class PueTest extends TestCase {

    public void testIncrementalEnergyOverhead() {
        double result = Pue.incrementalEnergyOverhead(800);
        Assert.assertEquals(464.00, result, 0);
    }

}