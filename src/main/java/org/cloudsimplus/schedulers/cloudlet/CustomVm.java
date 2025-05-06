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

    // remaining MIPS (disabled - likely to have been broken by refactoring)
    /*
    private final Map<Integer, Map<Integer, Double>> vcpuMipsRemainingMap = new HashMap<>(); // vCPU -> Cloudlet ID -> MIPS Used

    public Map<Integer, Map<Integer, Double>> getVcpuMipsRemainingMap() {
        return vcpuMipsRemainingMap;
    }*/

    // consumed MIPS - accumulated total
    // for each VM, a map VCPUs (for each VCPU, a map of all cloudlets)
    private final Map<Integer, Map<Integer, Double>> vcpuMipsConsumedMap = new HashMap<>();

    public Map<Integer, Map<Integer, Double>> getVcpuMipsConsumedMap() {
        return vcpuMipsConsumedMap;
    }

    /**
     * mipsRemaining (via getRemainingCloudletLength) is not a reliable source for unlimited length continuously running cloudlets
     * replaced with a measure of what has been done so far (via getFinishedLengthSoFar)
     * @param vcpuIndex
     * @param cloudletId
     * @param mipsDoneSoFar
     */
    public void trackCloudletMipsUsage(int vcpuIndex, int cloudletId, double mipsDoneSoFar) {
        /*
        // calculate remaining work
        vcpuMipsRemainingMap.putIfAbsent(vcpuIndex, new HashMap<>());

        // get the existing MIPS usage for this cloudlet on this vCPU
        double existingMipsRemaining = vcpuMipsRemainingMap.get(vcpuIndex).getOrDefault(cloudletId, 0.0);

        // ensure we don't double count by only adding new MIPS usage since the last update
        double newMipsRemaining = mipsDoneSoFar - existingMipsRemaining;

        vcpuMipsRemainingMap.get(vcpuIndex).put(cloudletId, existingMipsRemaining + newMipsRemaining);
        */

        // calculate work done (progress since last time)...
        vcpuMipsConsumedMap.putIfAbsent(vcpuIndex, new HashMap<>()); // if empty for this vcpu, then create

        double existingMips = vcpuMipsConsumedMap.get(vcpuIndex).getOrDefault(cloudletId, 0.0);
        double mipsSinceLastTime = mipsDoneSoFar - existingMips;

        //if(existingMipsRemaining != 0) { // don't add in the initial iteration
            vcpuMipsConsumedMap.get(vcpuIndex).put(cloudletId, existingMips + mipsSinceLastTime);
        //}
    }

    public final void overwriteMips(final double mips) {
        super.setMips(mips);
    }

}