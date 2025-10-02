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

    /**
     * vary the number of requests in a batch and the frequency of the batches - 2 random elements
     * @param currentTime
     * @param vmList
     * @return
     */
    @Override
    public List<Cloudlet> generateWorkloadAtTime(double currentTime, List<Vm> vmList) {
        List<Cloudlet> list = new ArrayList<>();

        // if we've passed the next arrival time, generate a new request (per VM)
        if (currentTime >= nextArrivalTime) {

            // don't create number of cloudlets based on server count, it shouldn't be 1:1
            // it helps to treat all servers equally (for debugging), but its not realistic
            //for (Vm vm : vmList) {

            //double resourceUtilisation = 0.25; // e.g. a cloudlet can use 25% of RAM
            double resourceUtilisation = 0.0625; // 1/16 - i.e. divide equally across all cores, so that a server can be maxed out

            // 1 / resourceUtilisation - how many concurrent requests a server can handle, before queueing/dropping - useful for throttling
            int maxRequestCount = 16 * vmList.size();

            int requestCount = (int)(Math.random() * maxRequestCount) + 1;
            //LOGGER.info("Creating Web Request Batch : " + requestCount);

            for(int i = 0; i < requestCount; i++) {
                Cloudlet cloudlet = new CloudletSimpleFixed(cloudletLength, cloudletPes, new UtilizationModelFull());

                final var utilizationModel = new UtilizationModelDynamic(1);
                UtilizationModelDynamic utilizationModelMemory = new UtilizationModelDynamic(resourceUtilisation);
                cloudlet.setUtilizationModel(utilizationModel);
                cloudlet.setUtilizationModelRam(utilizationModelMemory);
                cloudlet.setUtilizationModelBw(utilizationModelMemory);

                //cloudlet.setVm(vm);

                cloudlet.addOnStartListener(event -> {
                    //LOGGER.info(event.getTime() + " : Cloudlet Started : " + event.getCloudlet().getId() + " on VM : " + event.getCloudlet().getVm().getId() + " on HOST : " + event.getCloudlet().getVm().getHost().getId());
                });

                // note - any cloudlets that fail, won't trigger this (i.e. we aren't accumulating MIPS that we shouldn't be!)
                cloudlet.addOnFinishListener(event -> {
                    //LOGGER.info(event.getTime() + " : Cloudlet Completed : " + event.getCloudlet().getId() + " on VM : " + event.getCloudlet().getVm().getId() + " on HOST : " + event.getCloudlet().getVm().getHost().getId());
                    totalAccumulatedMips = totalAccumulatedMips.add(BigInteger.valueOf(event.getCloudlet().getTotalLength()));
                });

                // note - cloudlets that can't get resource immediately (over-provisioned), should be queued
                // initially, they seem to end up in a corrupt state where they use CPU but don't complete
                // this only seems to happen for the first few requests, then they queue properly and are eventually processed
                // to work around this problem (it distorts utilisation stats), we cancel everything that is over-provisioned
                // this is somewhat like the behaviour of a webapp (where requests quickly timeout)

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
                surely its just because of uniform distribution within the randomness
             */
            //nextArrivalTime += (int)(Math.random() * 10) + 1;

            //LOGGER.info("nextArrivalTime : " + nextArrivalTime + " gap : " + (nextArrivalTime - currentTime));
        }

        return list;
    }

}