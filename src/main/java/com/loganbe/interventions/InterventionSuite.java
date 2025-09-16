package com.loganbe.interventions;

import com.loganbe.Main;

/**
 * representing a suite of GreenIT interventions that can be applied before the simulation executes
 * this could constitute a major PhD and framework contribution, with more work
 */
public class InterventionSuite {

    public void applyInterventions(Main simulation) {
        new EfficientServers(simulation);
        //new PowerfulServers(simulation);
    }

}