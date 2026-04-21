package com.loganbe;

/**
 * simple object holding various simulation configuration parameters
 */
public class SimulationConfig {

    /*
     defines the time intervals to keep hosts CPU utilisation history records
     a scheduling interval is required to gather CPU utilisation statistics
     this number can dramatically effect the accuracy/realism of the simulation and the utilisation-based metrics
     earlier versions of the simulator ran with a value of 0.1, but 1 second seems a good compromise
     extending the interval (without going so far as to impact work allocation/utilisation) enables the framework to scale (i.e. improves performance)
     running with a reduced sampling/scheduling frequency, requires the custom utilisation logic (work done approach)
     as the allocation-based framework utilisation metric will be distorted/innacurate
     */
    public static final double SCHEDULING_INTERVAL = 1; // defaults to 0! i.e. continuous processing

    public static final double MIN_TIME_BETWEEN_EVENTS = 0.01;

    //public static final int DURATION = 60; // 1m (in seconds)
    public static final int DURATION = 60 * 60; // 1hr
    //public static final int DURATION = 60 * 60 * 24; // 24hrs

    public static final int ACCEPTABLE_WORKLOAD_ERROR = 5; // acceptable percentage error for workload validation

}