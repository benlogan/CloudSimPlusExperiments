package com.loganbe;

/**
 * simple object holding various simulation configuration parameters
 */
public class SimulationConfig {

    /*
      defines the datacenter scheduling interval for event processing.
      This controls how often the datacenter scheduler runs and processes events.
      defaults to 0.1 seconds for reasonable accuracy.
      */
    public static final double SCHEDULING_INTERVAL = 1; // datacenter scheduling interval

    /*
      defines the sampling interval for metrics collection (independent of SCHEDULING_INTERVAL).
      This controls how often SamplingMetrics takes samples for charts and utilization calculations.
      Use this to change sampling frequency without affecting datacenter scheduler behavior.
      For optimal accuracy, match this to SCHEDULING_INTERVAL (no point sampling faster than sim changes).
      */
    public static final double SAMPLING_INTERVAL = SCHEDULING_INTERVAL; // metrics sampling interval (seconds)

    public static final double MIN_TIME_BETWEEN_EVENTS = 0.01;

    /**
     * When true, cloudlet-specific MIPS history stored on each CustomVm is cleared as soon as a cloudlet finishes.
     * Flip this to false to restore the previous always-retain behavior for comparison/testing.
     */
    public static final boolean CLEAR_CLOUDLET_MIPS_HISTORY_ON_FINISH = true;

    /**
     * When true, the custom broker stops retaining cloudlets in its submitted/created/finished history lists.
     * This keeps the broker focused on the live working set instead of accumulating every cloudlet ever seen.
     */
    public static final boolean PRUNE_CLOUDLET_BROKER_HISTORY_ON_FINISH = true;

    /**
     * When true, scheduler history lists are pruned for each cloudlet as soon as it finishes.
     * This targets scheduler-level retention such as submitted/returned/finished cloudlet history.
     */
    public static final boolean PRUNE_CLOUDLET_SCHEDULER_HISTORY_ON_FINISH = true;

    /**
     * Enables CloudSim's scheduler submitted-cloudlet history list.
     * Keep false for scale runs to avoid retaining every submitted cloudlet in scheduler state.
     */
    public static final boolean ENABLE_SCHEDULER_SUBMITTED_LIST_HISTORY = false;

    /**
     * Enables periodic logging of cloudlet lifecycle counts (created, finished, and live scheduler entries).
     * Useful for confirming whether completed cloudlets are actually leaving the live working set.
     */
    public static final boolean LOG_CLOUDLET_LIFECYCLE_DIAGNOSTICS = true;

    /**
     * How often, in simulation seconds, to print the cloudlet lifecycle diagnostic summary.
     */
    public static final double CLOUDLET_LIFECYCLE_DIAGNOSTICS_INTERVAL = 60.0;

    //public static final int DURATION = 60; // 1m (in seconds)
    public static final int DURATION = 60 * 60; // 1hr
    //public static final int DURATION = 60 * 60 * 24; // 24hrs

    public static final int ACCEPTABLE_WORKLOAD_ERROR = 5; // acceptable percentage error for workload validation

}