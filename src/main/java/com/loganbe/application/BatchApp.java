package com.loganbe.application;

import com.loganbe.SimulationConfig;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimpleFixed;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BatchApp extends AbstractAppModel {

    private final int cloudletCount;

    public BatchApp(long cloudletLength, int cloudletCount, int cloudletPes) {
        this.cloudletLength = cloudletLength;
        this.cloudletCount = cloudletCount;
        this.cloudletPes = cloudletPes;
    }

    @Override
    public List<Cloudlet> generateInitialWorkload(List<CustomVm> vmList) {
        List<Cloudlet> list = new ArrayList<>();
        for (int i = 0; i < cloudletCount; i++) {
            Cloudlet cloudlet = new CloudletSimpleFixed(cloudletLength, cloudletPes, new UtilizationModelFull());
            cloudlet.setVm(vmList.get(i));

            cloudlet.addOnFinishListener(event -> {
                totalAccumulatedMi = totalAccumulatedMi.add(BigInteger.valueOf(event.getCloudlet().getTotalLength()));

                if (SimulationConfig.CLEAR_CLOUDLET_MIPS_HISTORY_ON_FINISH
                        && event.getCloudlet().getVm() instanceof CustomVm customVm) {
                    customVm.clearCloudletTracking((int) event.getCloudlet().getId());
                }

            });

            list.add(cloudlet);
            cloudletCreatedCounter = cloudletCreatedCounter.add(BigInteger.valueOf(1));
        }
        return list;
    }

    @Override
    public List<Cloudlet> generateWorkloadAtTime(double currentTime, List<CustomVm> vm) {
        return Collections.emptyList(); // batch jobs don’t generate more
    }

}