package com.loganbe.application;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimpleFixed;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WebApp implements ApplicationModel {

    private final long requestLength;
    private final double arrivalInterval; // e.g. 1.0 = 1 request/sec

    private double nextArrivalTime;

    public WebApp(long requestLength, double arrivalInterval) {
        this.requestLength = requestLength;
        this.arrivalInterval = arrivalInterval;
        this.nextArrivalTime = 0.0; // first request at t=0
    }

    @Override
    public List<Cloudlet> generateInitialWorkload(List<Vm> vmList) {
        return Collections.emptyList(); // starts empty
    }

    @Override
    public List<Cloudlet> generateWorkloadAtTime(double currentTime, List<Vm> vmList) {
        List<Cloudlet> list = new ArrayList<>();

        // if we've passed the next arrival time, generate a new request
        if (currentTime >= nextArrivalTime) {
            for (Vm vm : vmList) {
                Cloudlet cloudlet = new CloudletSimpleFixed(requestLength, (int) vm.getPesNumber(), new UtilizationModelFull());

                final var utilizationModel = new UtilizationModelDynamic(1);
                UtilizationModelDynamic utilizationModelMemory = new UtilizationModelDynamic(0.25); // 25% RAM
                cloudlet.setUtilizationModel(utilizationModel);
                cloudlet.setUtilizationModelRam(utilizationModelMemory);
                cloudlet.setUtilizationModelBw(utilizationModelMemory);

                cloudlet.setVm(vm);
                list.add(cloudlet);
            }

            // FIXME do I need to ensure that the previous 'request' (i.e. cloudlet) will have completed, in the interval
            // for now, just use a value that we can expect to have completed, can improve this code later

            // schedule the next arrival
            nextArrivalTime += arrivalInterval;
        }

        return list;
    }

}