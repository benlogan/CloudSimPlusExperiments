package com.loganbe.interventions;

import com.loganbe.Main;

public class InterventionSuite {

    private Main simulation;

    public InterventionSuite(Main simulation) {
        this.simulation = simulation;
    }

    public void applyInterventions() {
        new EfficientServers(simulation);
    }

}