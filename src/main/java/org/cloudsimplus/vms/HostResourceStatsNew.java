package org.cloudsimplus.vms;

import org.cloudsimplus.hosts.Host;

import java.util.function.Function;

public class HostResourceStatsNew extends HostResourceStats {
    /**
     * Creates a HostResourceStats to collect resource utilization statistics for a Host.
     *
     * @param machine                     the Host where the statistics will be collected
     * @param resourceUtilizationFunction a {@link Function} that receives a Host
     *                                    and returns the current resource utilization for that Host
     */
    public HostResourceStatsNew(Host machine, Function<Host, Double> resourceUtilizationFunction) {
        super(machine, resourceUtilizationFunction);
    }

    @Override
    public boolean add(final double time) {
        return super.add(time);

        // FIXME getting somewhere - I can now try to fix this bug, assuming it exists!
        // WORKINGHERE - have a workaround, but would be good to fix this properly...

        // maybe a quick fix is that I don't let the machine become idle/inactive!?
        // YES, that worked. so definitely a bug!!! now finally reporting correctly 100% CPU usage

        /*
        try {
            if (isNotTimeToAddHistory(time)) {
                return false;
            }

            final double utilization = resourceUtilizationFunction.apply(machine);
            /*If (i) the previous utilization is not zero and the current utilization is zero
             * and (ii) those values don't change, it means the machine has finished
             * and this utilization must not be collected.
             * If that happens, it may reduce accuracy of the utilization mean.
             * For instance, if a machine uses 100% of a resource all the time,
             * when it finishes, the utilization will be zero.
             * If that utilization is collected, the mean won't be 100% anymore.*/ /*
            if((previousUtilization != 0 && utilization == 0) || (machine.isIdle() && previousUtilization > 0)) {
                this.previousUtilization = utilization;
                return false;
            }

            this.stats.addValue(utilization);
            this.previousUtilization = utilization;
            return true;
        } finally {
            this.previousTime = machine.isIdle() ? time : (int)time;
        }*/
    }
}