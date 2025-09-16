package com.loganbe.templates.cloudlet;

/**
 * object representing web app configuration details (loaded from file)
 */
public class WebAppSpecification {

    private int cloudlet_length;

    private int arrival_interval;

    public int getCloudlet_length() {
        return cloudlet_length;
    }

    public void setCloudlet_length(int cloudlet_length) {
        this.cloudlet_length = cloudlet_length;
    }

    public int getArrival_interval() {
        return arrival_interval;
    }

    public void setArrival_interval(int arrival_interval) {
        this.arrival_interval = arrival_interval;
    }
}