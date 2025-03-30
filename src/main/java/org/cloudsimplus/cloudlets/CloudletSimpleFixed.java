package org.cloudsimplus.cloudlets;

import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.utilizationmodels.UtilizationModel;

public class CloudletSimpleFixed extends CloudletSimple {

    public CloudletSimpleFixed(long length, int pesNumber, UtilizationModel utilizationModel) {
        super(length, pesNumber, utilizationModel);
    }

    @Override
    public long getTotalLength() {
        // critical fix - should be using the assigned vm cores - not the number for the cloudlet

        // if space scheduler - will only run on one VM/Core
        if(this.getVm().getCloudletScheduler() instanceof CloudletSchedulerSpaceShared) {
            return getLength();
        } else { // else if time scheduler, work will be split/shared and will, effectively, multiply!
            return getLength() * getVm().getPesNumber();
        }
    }

}