package com.loganbe.interventions;

import com.loganbe.Main;

/**
 * representing a suite of GreenIT interventions that can be applied before the simulation executes
 * this could constitute a major PhD and framework contribution, with more work
 */
public class InterventionSuite {

    private Main simulation;

    public InterventionSuite(Main simulation) {
        this.simulation = simulation;
    }

    public void applyInterventions() {
        new EfficientServers(simulation);
    }

}