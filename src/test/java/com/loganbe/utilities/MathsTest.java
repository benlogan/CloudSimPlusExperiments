package com.loganbe.utilities;

import junit.framework.TestCase;
import org.junit.Assert;

public class MathsTest extends TestCase {

    public void testQuickRound() {
        double result = Maths.quickRound(33.3333333);
        Assert.assertEquals(33.33, result, 0);
    }

    public void testScaleAndRound() {
        double result = Maths.scaleAndRound(44.456789);
        Assert.assertEquals(44.46, result, 0);
    }

}