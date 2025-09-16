package com.loganbe.application;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.math.BigInteger;
import java.util.List;

public class AbstractAppModel implements ApplicationModel {

    public long cloudletLength;

    // FIXME probably needs to be reset, during multi-run sims
    public BigInteger totalAccumulatedMips = BigInteger.valueOf(0);

    public AbstractAppModel(long cloudletLength) {
        this.cloudletLength = cloudletLength;
    }

    public AbstractAppModel() {}

    @Override
    public List<Cloudlet> generateInitialWorkload(List<Vm> vmList) {
        return List.of();
    }

    @Override
    public List<Cloudlet> generateWorkloadAtTime(double currentTime, List<Vm> vmList) {
        return List.of();
    }
}
