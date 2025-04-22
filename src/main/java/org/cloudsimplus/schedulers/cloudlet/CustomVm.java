package org.cloudsimplus.schedulers.cloudlet;

import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomVm extends VmSimple {

    private List<Long> vcpuUsageHistory;

    public CustomVm(int mips, int numberOfCores) {
        super(mips, numberOfCores);
        // Create a vCPU usage history array
        vcpuUsageHistory = new ArrayList<>();
        for (int i = 0; i < numberOfCores; i++) {
            vcpuUsageHistory.add(-1l);  // -1 means no Cloudlet is running on this vCPU
        }
    }

    // Method to track vCPU assignment for a Cloudlet
    public void trackVcpuUsage(int vcpuIndex, long cloudletId) {
        vcpuUsageHistory.set(vcpuIndex, cloudletId);
    }

    public List<Long> getVcpuUsageHistory() {
        return vcpuUsageHistory;
    }

    private final Map<Integer, Map<Integer, Double>> vcpuMipsUsageMap = new HashMap<>(); // vCPU -> Cloudlet ID -> MIPS Used

    private final Map<Integer, Map<Integer, Double>> vcpuMipsUsageMapNew = new HashMap<>();

    public void trackCloudletMipsUsage(int vcpuIndex, int cloudletId, double mipsRemaining) {
        vcpuMipsUsageMap.putIfAbsent(vcpuIndex, new HashMap<>());
        vcpuMipsUsageMapNew.putIfAbsent(vcpuIndex, new HashMap<>());

        // get the existing MIPS usage for this cloudlet on this vCPU
        double existingMipsRemaining = vcpuMipsUsageMap.get(vcpuIndex).getOrDefault(cloudletId, 0.0);

        // ensure we don't double count by only adding new MIPS usage since the last update
        double newMipsRemaining = mipsRemaining - existingMipsRemaining;

        vcpuMipsUsageMap.get(vcpuIndex).put(cloudletId, existingMipsRemaining + newMipsRemaining);

        double existingMips = vcpuMipsUsageMapNew.get(vcpuIndex).getOrDefault(cloudletId, 0.0);
        if(existingMipsRemaining != 0) { // don't add in the initial iteration
            vcpuMipsUsageMapNew.get(vcpuIndex).put(cloudletId, existingMips + Math.abs(newMipsRemaining));
        }
    }

    // remaining
    public Map<Integer, Map<Integer, Double>> getVcpuMipsUsageMap() {
        return vcpuMipsUsageMap;
    }

    // used - accumulated total
    public Map<Integer, Map<Integer, Double>> getVcpuMipsUsageMapNew() {
        return vcpuMipsUsageMapNew;
    }

    public final void overwriteMips(final double mips) {
        super.setMips(mips);
    }

}