package com.loganbe.application;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;

import java.math.BigInteger;
import java.util.List;

public class AbstractAppModel implements ApplicationModel {

    public long cloudletLength;
    public int cloudletPes;

    public BigInteger totalAccumulatedMi = BigInteger.valueOf(0);
    public BigInteger totalAccumulatedMiAll = BigInteger.valueOf(0); // not just completed, but everything!

    public BigInteger cloudletCounter = BigInteger.valueOf(0);

    public AbstractAppModel() {}

    @Override
    public List<Cloudlet> generateInitialWorkload(List<CustomVm> vmList) {
        return List.of();
    }

    @Override
    public List<Cloudlet> generateWorkloadAtTime(double currentTime, List<CustomVm> vmList) {
        return List.of();
    }

}