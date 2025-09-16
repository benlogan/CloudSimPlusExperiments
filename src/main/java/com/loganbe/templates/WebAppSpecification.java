package com.loganbe.templates;

/**
 * object representing web app configuration details (loaded from file)
 */
public class WebAppSpecification {

    private int request_length;

    private int arrival_interval;

    public int getRequest_length() {
        return request_length;
    }

    public void setRequest_length(int request_length) {
        this.request_length = request_length;
    }

    public int getArrival_interval() {
        return arrival_interval;
    }

    public void setArrival_interval(int arrival_interval) {
        this.arrival_interval = arrival_interval;
    }
}