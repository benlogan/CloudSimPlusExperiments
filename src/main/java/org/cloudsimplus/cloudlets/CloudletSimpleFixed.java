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

        // if space scheduler - will only run on one VM (but should still use all cores - or rather, all cores that you've asked for!)
        if(this.getVm().getCloudletScheduler() instanceof CloudletSchedulerSpaceShared) {
            //return getLength();
            //return getLength() * getVm().getPesNumber();
            return getLength() * getPesNumber(); // this is critical - how many cores have you asked to use!?
        } else { // else if time scheduler, work will be split/shared and will, effectively, multiply!
            return getLength() * getVm().getPesNumber();
        }
    }

}