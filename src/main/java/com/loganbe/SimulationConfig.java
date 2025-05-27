package com.loganbe;

/**
 * simple object holding various simulation configuration parameters
 */
public class SimulationConfig {

    // defines the time intervals to keep hosts CPU utilisation history records
    // a scheduling interval is required to gather CPU utilisation statistics
    public static final double SCHEDULING_INTERVAL = 0.1; // defaults to 0! i.e. continuous processing

    public static final int DURATION = 60 * 60; // 1hr (in seconds)
    //public static final int DURATION = 60 * 60 * 24; // 24hrs

    public static final int ACCEPTABLE_WORKLOAD_ERROR = 5; // acceptable percentage error for workload validation

}
