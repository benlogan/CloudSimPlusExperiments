package com.loganbe.application;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimpleFixed;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WebApp extends AbstractAppModel {

    public static final Logger LOGGER = LoggerFactory.getLogger(WebApp.class.getSimpleName());

    private final double arrivalInterval; // e.g. 1.0 = 1 request/sec

    private double nextArrivalTime;

    public WebApp(long cloudletLength, double arrivalInterval, int cloudletPes) {
        this.cloudletLength = cloudletLength;
        this.arrivalInterval = arrivalInterval;
        this.nextArrivalTime = 0.0; // first request at t=0
        this.cloudletPes = cloudletPes;
    }

    @Override
    public List<Cloudlet> generateInitialWorkload(List<Vm> vmList) {
        return Collections.emptyList(); // starts empty
    }

    @Override
    public List<Cloudlet> generateWorkloadAtTime(double currentTime, List<Vm> vmList) {
        List<Cloudlet> list = new ArrayList<>();

        // if we've passed the next arrival time, generate a new request (per VM)
        if (currentTime >= nextArrivalTime) {
            for (Vm vm : vmList) { // FIXME don't create number of cloudlets based on servers - this is not remotely webapp like!
                Cloudlet cloudlet = new CloudletSimpleFixed(cloudletLength, cloudletPes, new UtilizationModelFull());

                final var utilizationModel = new UtilizationModelDynamic(1);
                UtilizationModelDynamic utilizationModelMemory = new UtilizationModelDynamic(0.25); // 25% RAM
                cloudlet.setUtilizationModel(utilizationModel);
                cloudlet.setUtilizationModelRam(utilizationModelMemory);
                cloudlet.setUtilizationModelBw(utilizationModelMemory);

                cloudlet.setVm(vm);

                cloudlet.addOnFinishListener(event -> {
                    totalAccumulatedMips = totalAccumulatedMips.add(BigInteger.valueOf(event.getCloudlet().getTotalLength()));
                });

                list.add(cloudlet);
            }

            // FIXME do I need to ensure that the previous 'request' (i.e. cloudlet) will have completed, in the interval
            // for now, just use a value that we can expect to have completed, can improve this code later

            // schedule the next arrival (constant, predictable load)
            nextArrivalTime += arrivalInterval;

            /*
                WORKINGHERE
                introducing some randomness, to simulate real-world webapp behavior (variable load)
                while varying this from 1-10, to 1-30, will significantly impact the work done
                it won't for multiple runs with similar randomness - need to understand that better
                I think its because I'm massively over-provisioned, so its not a determining factor in work done
             */
            //nextArrivalTime += (int)(Math.random() * 30) + 1;
            //LOGGER.info("nextArrivalTime : " + nextArrivalTime);
        }

        return list;
    }

}