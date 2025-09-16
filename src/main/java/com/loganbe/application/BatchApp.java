package com.loganbe.application;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimpleFixed;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BatchApp implements ApplicationModel {

    private final int cloudletCount;
    private final long cloudletLength;

    public BatchApp(long cloudletLength, int cloudletCount) {
        this.cloudletLength = cloudletLength;
        this.cloudletCount = cloudletCount;
    }

    @Override
    public List<Cloudlet> generateInitialWorkload(List<Vm> vmList) {
        List<Cloudlet> list = new ArrayList<>();
        for (int i = 0; i < cloudletCount; i++) {
            Cloudlet cloudlet = new CloudletSimpleFixed(cloudletLength, (int) vmList.get(i).getPesNumber(), new UtilizationModelFull());
            cloudlet.setVm(vmList.get(i));
            list.add(cloudlet);
        }
        return list;
    }

    @Override
    public List<Cloudlet> generateWorkloadAtTime(double currentTime, List<Vm> vm) {
        return Collections.emptyList(); // batch jobs don’t generate more
    }
}