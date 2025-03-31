package org.cloudsimplus.schedulers.cloudlet;

import org.cloudsimplus.cloudlets.CloudletExecution;
import org.cloudsimplus.vms.Vm;

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

    @Override
    public double updateCloudletProcessing(final CloudletExecution cle, final double currentTime) {
        Vm vm = cle.getCloudlet().getVm();
        if (!(vm instanceof CustomVm)) {
            return super.updateCloudletProcessing(cle, currentTime);
        }
        CustomVm customVm = (CustomVm) vm;

        int cloudletId = (int) cle.getCloudlet().getId();
        int numberOfVcpus = (int) customVm.getPesNumber();
        double totalVmMips = customVm.getMips();

        // Get active cloudlets
        int activeCloudlets = getCloudletExecList().size();
        double mipsPerCloudlet = totalVmMips / Math.max(1, activeCloudlets);

        // Track MIPS usage on each vCPU for each cloudlet
        for (int i = 0; i < numberOfVcpus; i++) {
            if (i < activeCloudlets) {
                customVm.trackCloudletMipsUsage(i, cloudletId, cle.getRemainingCloudletLength());
                /*
                if(cloudletId == 0) {
                    System.out.println("Time: " + currentTime + " | Cloudlet " + cloudletId + " is running on vCPU " + i + " with " + mipsPerCloudlet + " MIPS. getRemainingCloudletLength : " + cle.getRemainingCloudletLength());
                    System.out.println("accumulated MIPS : " + customVm.getVcpuMipsUsageMapNew().get(i).get(cloudletId));
                }
                */
            }
        }

        return super.updateCloudletProcessing(cle, currentTime);
    }

}