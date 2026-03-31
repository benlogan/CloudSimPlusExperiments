package com.loganbe.utilities;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;

import java.util.List;
import java.util.Map;

public class CloudletPrinter {

    /**
     * prints cloudlet execution MIPS and percentages, per VCPU
     * e.g.
     * Cloudlet 0 ran on VM 0 on vCPU 0 for 3428191.0 MIPS, which is 6.25% of total execution.
     * @param vmList
     */
    public static void printCloudletExecutionStatsPerVcpu(DatacenterBroker broker, List<CustomVm> vmList) {
        for(CustomVm vm : vmList) {
            Map<Integer, Map<Integer, Double>> vcpuMipsUsageMap = vm.getVcpuMipsConsumedMap();

            // for each vCPU
            for (Map.Entry<Integer, Map<Integer, Double>> vcpuEntry : vcpuMipsUsageMap.entrySet()) {
                int vcpuIndex = vcpuEntry.getKey();
                Map<Integer, Double> cloudletMipsMap = vcpuEntry.getValue();

                // For each cloudlet, calculate MIPS usage and percentage
                for (Map.Entry<Integer, Double> cloudletEntry : cloudletMipsMap.entrySet()) {
                    int cloudletId = cloudletEntry.getKey();
                    double mipsUsed = cloudletEntry.getValue();
                    //double totalCloudletMips = 0.0;

                    // calculate total MIPS used by cloudlet (across all vCPUs)
                    /*
                    for (Map<Integer, Double> mipsUsage : vcpuMipsUsageMap.values()) {
                        totalCloudletMips += mipsUsage.getOrDefault(cloudletId, 0.0);
                    }
                    */

                    // calculate percentage of execution time on this vCPU for this cloudlet
                    // will always simply be the percentage split across cores, regardless of completeness
                    //double percentage = (mipsUsed / totalCloudletMips) * 100;

                    double totalLength = 0;
                    // more useful - calculate percentage completeness of overall cloud execution
                    // double percentage = (mipsUsed / vm.getCloudletScheduler().getCloudletList().get(cloudletId).getTotalLength()) * 100;
                    // FIXME total length not behaving - doesn't seem to actually use getPesNumber! or rather uses it for the cloudlet, not the VM
                    // FIXME also must be a more efficient way of finding a cloudlet by ID (without this iteration and without going via the broker)
                    for (Cloudlet c : broker.getCloudletCreatedList()) {
                        if (c.getId() == cloudletId) {
                            totalLength = c.getTotalLength();
                        }
                    }
                    double percentage = (mipsUsed / totalLength) * 100;
                    // FIXME round small numbers to zero, later

                    System.out.println("Cloudlet " + cloudletId + " ran on VM " + vm.getId() + " on vCPU " + vcpuIndex + " for " + mipsUsed + " MIPS, which is " + percentage + "% of total execution.");
                }
            }
        }
    }

    /**
     * very similar to printCloudletExecutionStatsPerVcpu
     * same data, just in tabular format, e.g.
     * | VM ID      | vCPU Index | Cloudlet ID | MIPS Used    | Execution % |
     * ----------------------------------------------------------------------
     * | 0          | 0          | 0           | 3428191.00   | 6.25        |
     * @param vmList
     */
    public static void printCloudletExecutionStatsPerVcpuTabular(DatacenterBroker broker, List<CustomVm> vmList) {
        // display header
        System.out.println("----------------------------------------------------------------------");
        System.out.println(String.format("| %-10s | %-10s | %-11s | %-12s | %-11s |", "VM ID", "vCPU Index", "Cloudlet ID", "MIPS Used", "Execution %"));
        System.out.println("----------------------------------------------------------------------");

        for(CustomVm vm : vmList) {
            Map<Integer, Map<Integer, Double>> vcpuMipsUsageMap = vm.getVcpuMipsConsumedMap();

            // check - if you are not using the new custom scheduler, this won't be populated!
            if(vm.getVcpuMipsConsumedMap().size() == 0) {
                System.err.println("getVcpuMipsConsumedMap IS EMPTY!");
            }

            // iterate through the vCPU and cloudlet MIPS usage map
            for (Map.Entry<Integer, Map<Integer, Double>> vcpuEntry : vcpuMipsUsageMap.entrySet()) {
                int vcpuIndex = vcpuEntry.getKey();
                Map<Integer, Double> cloudletMipsMap = vcpuEntry.getValue();

                // for each cloudlet on this vCPU
                for (Map.Entry<Integer, Double> cloudletEntry : cloudletMipsMap.entrySet()) {
                    int cloudletId = cloudletEntry.getKey();
                    double mipsUsed = cloudletEntry.getValue();

                    // calculate total MIPS used by the cloudlet (across all vCPUs)
                    /*
                    double totalCloudletMips = 0.0;
                    for (Map<Integer, Double> mipsUsage : vcpuMipsUsageMap.values()) {
                        totalCloudletMips += mipsUsage.getOrDefault(cloudletId, 0.0);
                    }
                    */

                    // calculate percentage of execution time for this cloudlet on this vCPU
                    //double percentage = (mipsUsed / totalCloudletMips) * 100;

                    double totalLength = 0;

                    // FIXME rather wastefully looking at all cloudlets when I shouldn't need to
                    for (Cloudlet c : broker.getCloudletCreatedList()) {
                        if (c.getId() == cloudletId) {
                            //totalLength = vm.getPesNumber() * c1.getTotalLength();
                            totalLength = c.getTotalLength();
                        }
                    }
                    double percentage = (mipsUsed / totalLength) * 100;

                    // print row for this cloudlet on this vCPU
                    System.out.println(String.format("| %-10d | %-10d | %-11d | %-12.2f | %-11.2f |", vm.getId(), vcpuIndex, cloudletId, mipsUsed, percentage));
                }
            }
        }

        // end of table
        System.out.println("----------------------------------------------------------------------");
    }

}