/*
 * extended in order to implement and test a critical fix to the processing of cloudlets
 * @author Ben Logan
 */
package com.loganbe;

import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;

import java.util.List;

public class DatacenterSimpleFixed extends DatacenterSimple {

    private final double EPSILON = 1E-9; // small tolerance value for precision errors

    public DatacenterSimpleFixed(Simulation simulation, List<? extends Host> hostList) {
        super(simulation, hostList);
    }

    /**
     * overridden in order to fix a major bug
     *
     * @param nextFinishingCloudletTime the predicted completion time of the earliest finishing cloudlet
     * (which is a relative delay from the current simulation time),
     * or {@link Double#MAX_VALUE} if there is no next Cloudlet to execute
     * @return
     */
    protected double getCloudletProcessingUpdateInterval(final double nextFinishingCloudletTime) {
        if(getSchedulingInterval() == 0) {
            return nextFinishingCloudletTime;
        }

        final double time = Math.floor(getSimulation().clock());
        final double mod = time % getSchedulingInterval();
        /* If a scheduling interval is set, ensures the next time that Cloudlets' processing
         * are updated is multiple of the scheduling interval.
         * If there is an event happening before such a time, then the event
         * will be scheduled as usual. Otherwise, the update
         * is scheduled to the next time multiple of the scheduling interval.*/

        // major bug with this approach, preventing successful completion of cloudlets
        //final double delay = mod == 0 ? getSchedulingInterval() : (time - mod + getSchedulingInterval()) - time;

        double delaySubComponent = (time - mod + getSchedulingInterval()) - time;
        if (Math.abs(delaySubComponent) < EPSILON) { // needed to deal with one or two floating point precision issues
            delaySubComponent = getSchedulingInterval();
        }
        final double delay = mod == 0 ? getSchedulingInterval() : delaySubComponent;

        return Math.min(nextFinishingCloudletTime, delay);
    }
}