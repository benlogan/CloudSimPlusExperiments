package com.loganbe;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

public class AnalysisTest extends TestCase {

    public void testCalculateDeltas() {
        Map<Integer, Double> energyMap = new HashMap<Integer, Double>() {
            {
                put(1, 2.0);
                put(2, 1.0);
            }
        };
        Map<Integer, Double> workMap = new HashMap<Integer, Double>() {
            {
                put(1, 10.0);
                put(2, 1.0);
            }
        };
        Analysis.calculateDeltas(energyMap, workMap);
    }

}