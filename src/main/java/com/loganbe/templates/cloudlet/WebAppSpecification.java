package com.loganbe.templates.cloudlet;

/**
 * object representing web app configuration details (loaded from file)
 */
public class WebAppSpecification {

    private int arrival_interval;

    public int getArrival_interval() {
        return arrival_interval;
    }

    public void setArrival_interval(int arrival_interval) {
        this.arrival_interval = arrival_interval;
    }
}