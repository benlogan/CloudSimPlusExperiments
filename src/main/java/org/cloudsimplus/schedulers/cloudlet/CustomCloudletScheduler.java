package org.cloudsimplus.schedulers.cloudlet;

import org.cloudsimplus.cloudlets.CloudletExecution;
import org.cloudsimplus.vms.Vm;

import java.util.HashMap;
import java.util.Map;

/**
 * using space shared - there will only be 1 cloudlet per VM
 */
public class CustomCloudletScheduler extends CloudletSchedulerSpaceShared {
//public class CustomCloudletScheduler extends CloudletSchedulerTimeShared {

    /*
    @Override
    public double updateCloudletProcessing(final CloudletExecution cle, final double currentTime) {
        if(cle.getCloudlet().getId() == 0) {
            System.out.println("updateCloudletProcessing currentTime : " + currentTime + " cloudlet ID : " + cle.getCloudlet().getId());

            System.out.println("getRemainingCloudletLength : " + cle.getRemainingCloudletLength());
            if(cle.getRemainingLifeTime() != Double.MAX_VALUE) {
                System.out.println("getRemainingLifeTime : " + cle.getRemainingLifeTime());
            }
        }
        Vm vm = cle.getCloudlet().getVm();
        if (vm instanceof CustomVm) {
            CustomVm customVm = (CustomVm) vm;
            // I think we need to actually simulate the vcpu core allocation here
            // i.e. take the load and distribute it across some cores that I map in the vm object
            // because cloudsimplus doesn't actually do this - the cores are an abstract concept
            // but this isn't being called for every instruction execution (is anything!?) - FIXME try
            // If you need per-instruction tracking, you’d need to modify CloudletExecution.

            // what would be useful, is to see;
            // what percentage of a given cloudlet executed on a particular core
            // and perhaps the number of MIPs (for each cloudlet and in total)
            // I doubt that requires per instruction tracking!
            // WORKING HERE - this is what I really need to do, this is the output I want to see

            // mental note - this is a bit of a rabbit hole that I can't really afford to go down for a part-time phd!
            // I think worth pursuing, given that it helps validate my understanding of how cloudsimplus works
            // and shows I can extend it etc, but generally I should try to avoid meddling under the hood after this!
            customVm.getId();

            long numberOfVcpus = customVm.getPesNumber();
            double totalVmMips = customVm.getMips();

            // Get active cloudlets
            int activeCloudlets = getCloudletExecList().size();
            double mipsPerCloudlet = totalVmMips / Math.max(1, activeCloudlets);

            // Map vCPUs to cloudlets
            for (int i = 0; i < numberOfVcpus; i++) {
                if (i < activeCloudlets) {
                    long assignedCloudlet = getCloudletExecList().get(i).getCloudlet().getId();
                    customVm.trackVcpuUsage(i, assignedCloudlet);
                    System.out.println("Time: " + currentTime + " | Cloudlet " + assignedCloudlet + " is running on vCPU " + i + " with " + mipsPerCloudlet + " MIPS.");
                }
            }
        }

        return super.updateCloudletProcessing(cle, currentTime);
    }*/

    /*
    // useful method to override if you are investigating why a cloudlet might be waiting for resources
    @Override
    protected boolean canExecuteCloudletInternal(final CloudletExecution cle) {

        //System.out.println(cle.getCloudlet().getVm().getProcessor().getAvailableResource());
        //System.out.println(cle.getPesNumber());

        // this correctly shows that the VM has capacity
        // but because we were using a shared scheduler object, there was a pointer to a different instance of the VM object that doesn't have capacity!

        if(isThereEnoughFreePesForCloudlet(cle)) {
            System.err.println("THERE IS ENOUGH CAPACITY TO RUN!");
        }

        return isThereEnoughFreePesForCloudlet(cle);
    }*/

    /*
    @Override
    public void setVm(final Vm vm) {
        System.out.println("SETTING VM IN SCHEDULER : " + vm.getId() + " uuid: " + vm.getUid() +  " host: " + vm.getHost() + " resources: " + vm.getProcessor().getAvailableResource());
        super.setVm(vm);
    }*/

    double lastUpdateTime = 0;

    @Override
    public double updateCloudletProcessing(final CloudletExecution cle, final double currentTime) {
        Vm vm = cle.getCloudlet().getVm();
        if (!(vm instanceof CustomVm)) {
            return super.updateCloudletProcessing(cle, currentTime);
        }
        CustomVm customVm = (CustomVm) vm;

        int cloudletId = (int) cle.getCloudlet().getId();
        //int numberOfVcpus = (int) customVm.getPesNumber(); // NOT how many cores do we have, but rather how many are we choosing to use!
        int numberOfVcpus = (int) cle.getCloudlet().getPesNumber(); // NOT how many cores do we have, but rather how many are we choosing to use!
        //double totalVmMips = customVm.getMips();

        // Get active cloudlets
        //int activeCloudlets = getCloudletExecList().size();
        //double mipsPerCloudlet = totalVmMips / Math.max(1, activeCloudlets);

        // Track MIPS usage on each vCPU for each cloudlet
        for (int i = 0; i < numberOfVcpus; i++) {
                //customVm.trackCloudletMipsUsage(i, cloudletId, cle.getRemainingCloudletLength()); // doesn't work when cloudlet length hasn't been specified
                // FIXME but we should perhaps use that method when we are dealing with fixed length cloudlets?
                //customVm.trackCloudletMipsUsage(i, cloudletId, (cle.getCloudlet().getFinishedLengthSoFar() / numberOfVcpus));
                customVm.trackCloudletMipsUsage(i, cloudletId, cle.getCloudlet().getFinishedLengthSoFar()); // it's MIPS per core, so this doesn't need dividing!
                /*
                if(cloudletId == 0) {
                    System.out.println("Time: " + currentTime + " | Cloudlet " + cloudletId + " is running on vCPU " + i + " with " + mipsPerCloudlet + " MIPS. getRemainingCloudletLength : " + cle.getRemainingCloudletLength());
                    System.out.println("accumulated MIPS : " + customVm.getVcpuMipsUsageMapNew().get(i).get(cloudletId));
                }
                */
        }

        //System.out.println("getCloudletExecList : " + getCloudletExecList().size());
        //System.out.println("getCloudletWaitingList : " + getCloudletWaitingList().size());
        // this shows that cloudlets are waiting when we wouldn't expect them to be
        // all hosts are doing the same thing - they all have one in execution at the same time (the same cloudlet!)

        // custom utilisation code - this is calculating actual utilisation!
        // the framework otherwise considers allocation alone to represent utilisation - not accurate!
        // this is closer to actual utilisation and so is more useful for energy calculations

        //System.err.println("PROCESSING ON ONE HOST AT A TIME : " + cle.getCloudlet().getVm().getHost() + " current time : " + currentTime);
        // IMPORTANT - this shows that we are only processing on one host at a time...
        // so we can use this logic to measure utilisation accurately;
        long hostId = cle.getCloudlet().getVm().getHost().getId();
        double timeDelta = currentTime - lastUpdateTime;
        hostElapsedTime.put(hostId, hostElapsedTime.getOrDefault(hostId, 0.0) + timeDelta);
        lastUpdateTime = currentTime;

        //System.out.println("Cloudlets in WAITING LIST " + getCloudletWaitingList().size());
        /*
        for (CloudletExecution cloudlet : getCloudletWaitingList()) {
            if (cloudlet.getCloudlet().getStatus() == Cloudlet.Status.QUEUED) {
                System.out.println("Cloudlet " + cloudlet.getCloudlet().getId() + " is waiting in the queue.");
            }
        }*/

        return super.updateCloudletProcessing(cle, currentTime);
    }

    private Map<Long, Double> hostElapsedTime = new HashMap<>();

    public double getHostElapsedTime(long hostId) {
        return hostElapsedTime.getOrDefault(hostId, 0.0);
    }
}