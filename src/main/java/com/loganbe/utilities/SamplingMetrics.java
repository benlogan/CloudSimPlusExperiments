package com.loganbe.utilities;

import com.loganbe.application.AbstractAppModel;
import com.loganbe.power.Carbon;
import com.loganbe.power.Power;
import com.loganbe.power.Sci;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.loganbe.utilities.Maths.SCALING_FACTOR;

public class SamplingMetrics {

    private static final int DEFAULT_CORES = 16;

    private final CloudSimPlus simulation;
    private final Datacenter datacenter;
    private final AbstractAppModel app;

    // sampling interval and end time - set these to the underlying sim variables governing sim duration
    private final double tickInterval; // e.g. 0.1 = every 1/10th second - expect 36,000 readings for the normal 1 hr simulation
    private final double endTime; // note this is not in ticks, but in time!

    // time series data structures used in sampling, for export/charting

    private final Map<Long, List<Double>> hostWork = new HashMap<>();               // work done (MI's), since last sample
    private final Map<Long, List<Double>> hostWorkCumulative = new HashMap<>();     // work done (MI's), running total

    private final Map<Long, List<Double>> hostUtil = new HashMap<>();               // using the custom methodology (work done)
    private final Map<Long, List<Double>> hostUtilAllocation = new HashMap<>();     // using the default framework methodology (allocation based)

    private final Map<Long, List<Double>> hostEnergy = new HashMap<>();             // current hourly energy rate (power) - NOT energy consumed since last sample (that would be much smaller)

    private final Map<Long, List<Double>> hostSci = new HashMap<>();                // point-in-time SCI
    private final Map<Long, List<Double>> hostSciCumulative = new HashMap<>();      // cumulative SCI (not point in time) - should converge with average for the sim run

    private final Map<Long, BigInteger> lastAccumulatedMips = new HashMap<>();

    private final AtomicLong cumulativeEnergyScaled = new AtomicLong();
    private final AtomicLong cumulativeEmbodied = new AtomicLong();

    private WorkloadGenerator workloadGenerator;

    private boolean started;

    public SamplingMetrics(final CloudSimPlus simulation,
                           final Datacenter datacenter,
                           final DatacenterBroker broker,
                           final AbstractAppModel app,
                           final List<CustomVm> vmList,
                           final List<Cloudlet> initialCloudlets,
                           final double tickInterval,
                           final double endTime) {
        this.simulation = simulation;
        this.datacenter = datacenter;
        this.app = app;
        this.tickInterval = tickInterval;
        this.endTime = endTime;
        this.workloadGenerator = new WorkloadGenerator(app, vmList, broker, new ArrayList<>(initialCloudlets));

        for (final Host host : datacenter.getHostList()) {
            lastAccumulatedMips.put(host.getId(), BigInteger.ZERO);
        }
    }

    public void start() {
        if (started) {
            return;
        }

        started = true;
        new Sampler(simulation, tickInterval, endTime, this::takeSample);
    }

    public void takeSample(final double time) {
        sampleHostMetrics();
        workloadGenerator.generateAndSubmitWorkload(time);
    }

    private void sampleHostMetrics() {
        for (final Host host : datacenter.getHostList()) {
            final long hostId = host.getId();

            // WORK DONE

            final BigInteger accumulatedWorkDelta = getAccumulatedMipsDelta(hostId);

            getOrCreateSeries(hostWork, hostId).add(accumulatedWorkDelta.doubleValue());
            getOrCreateSeries(hostWorkCumulative, hostId).add(app.totalAccumulatedMiAll.doubleValue());

            // UTILISATION

            //final double utilisationByAllocation = host.getCpuPercentUtilization(); // using the default framework approach (can be unreliable)
            //getOrCreateSeries(hostUtilAllocation, hostId).add(utilisationByAllocation * 100);

            final double utilisationByWorkDone = (accumulatedWorkDelta.doubleValue() / datacenter.getHostList().size()) / (tickInterval * 1000 * DEFAULT_CORES);
            getOrCreateSeries(hostUtil, hostId).add(utilisationByWorkDone * 100);

            // ENERGY

            final double currentEnergyRate = Power.calculateEnergy(host, utilisationByWorkDone);
            getOrCreateSeries(hostEnergy, hostId).add(currentEnergyRate);

            // Power.calculateEnergyEfficiencyFromHost(host, accumulatedWorkDelta); - not used at the moment

            final double currentEnergyPerInterval = (currentEnergyRate / 3600) / 10;
            cumulativeEnergyScaled.getAndAdd(Math.round(currentEnergyPerInterval * SCALING_FACTOR));

            // SCI

            final List<Double> sciTimeSeries = getOrCreateSeries(hostSci, hostId);
            final List<Double> sciCumTimeSeries = getOrCreateSeries(hostSciCumulative, hostId);

            final double cumulativeEnergyDescaled = cumulativeEnergyScaled.get() / 1_000_000.0;
            double operationalCumulative = Carbon.energyToCarbon(cumulativeEnergyDescaled);
            final double operational = Carbon.energyToCarbon(currentEnergyPerInterval);

            final double embodiedTotal = calculateAndAccumulateEmbodiedEmissions();
            sciTimeSeries.add(Sci.calculateSci(operational, embodiedTotal, accumulatedWorkDelta.doubleValue(), false));

            operationalCumulative = operationalCumulative * datacenter.getHostList().size();
            final double cumulativeEmbodiedTotal = cumulativeEmbodied.get() / (double) SCALING_FACTOR;
            sciCumTimeSeries.add(Sci.calculateSci(operationalCumulative, cumulativeEmbodiedTotal, app.totalAccumulatedMiAll.doubleValue(), false));
        }
    }

    private BigInteger getAccumulatedMipsDelta(final long hostId) {
        final BigInteger current = app.totalAccumulatedMiAll;
        final BigInteger previous = lastAccumulatedMips.getOrDefault(hostId, BigInteger.ZERO);
        lastAccumulatedMips.put(hostId, current);
        return current.subtract(previous);
    }

    private double calculateAndAccumulateEmbodiedEmissions() {
        double embodiedTotal = 0;
        for (final Host hostNested : datacenter.getHostList()) {
            final HostSimpleFixed hsf = (HostSimpleFixed) hostNested;
            embodiedTotal += hsf.embodiedEmissions;
        }

        embodiedTotal = Sci.amortizeEmbodiedToSample(embodiedTotal);
        cumulativeEmbodied.getAndAdd(Math.round(embodiedTotal * SCALING_FACTOR));
        return embodiedTotal;
    }

    /*
     * calculates average utilisation for each host (i.e. a single value), based on the samples taken during the sim run
     */
    public Map<Long, Double> calculateAverageUtilisation() {
        final Map<Long, Double> averageHostUtilisation = new HashMap<>();
        for (final Host host : datacenter.getHostList()) {
            final long hostId = host.getId();

            double sum = 0;
            for(double d : hostUtil.get(hostId)) {
                sum = sum + d;
            }
            final int count = hostUtil.get(hostId).size();

            double averageUtilisation = (sum / count) / 100;

            averageHostUtilisation.put(hostId, count == 0 ? 0.0 : averageUtilisation);
        }
        return averageHostUtilisation;
    }

    private static <T> List<T> getOrCreateSeries(final Map<Long, List<T>> map, final long hostId) {
        return map.computeIfAbsent(hostId, ignored -> new ArrayList<>());
    }

    public Map<Long, List<Double>> getHostEnergy() {
        return hostEnergy;
    }

    public Map<Long, List<Double>> getHostUtil() {
        return hostUtil;
    }

    public Map<Long, List<Double>> getHostUtilAllocation() {
        return hostUtilAllocation;
    }

    public Map<Long, List<Double>> getHostWork() {
        return hostWork;
    }

    public Map<Long, List<Double>> getHostWorkCumulative() {
        return hostWorkCumulative;
    }

    public Map<Long, List<Double>> getHostSci() {
        return hostSci;
    }

    public Map<Long, List<Double>> getHostSciCumulative() {
        return hostSciCumulative;
    }

}

// OLD CODE (remove once confirmed it has been replaced)
/*
        new Sampler(simulation, TICK_INTERVAL, END_TIME, t -> {
            tickCount.getAndIncrement();

            for (Host host : datacenter.getHostList()) {

                // CPU utilisation sampling...
                double utilisation = host.getCpuPercentUtilization();
                /*
                List<Double> utilTimeSeries;
                if(hostUtil.get(host.getId()) != null) {
                    utilTimeSeries = hostUtil.get(host.getId());
                } else {
                    utilTimeSeries = new ArrayList<>();
                }
                utilTimeSeries.add(utilisation * 100);
                hostUtil.put(host.getId(), utilTimeSeries);
                *//*

                // energy sampling...
                List<Double> energyRawTimeSeries;
                if(hostEnergyRaw.get(host.getId()) != null) {
                    energyRawTimeSeries = hostEnergyRaw.get(host.getId());
                } else {
                    energyRawTimeSeries = new ArrayList<>();
                }
                double currentEnergyRate = Power.calculateEnergy(host, utilisation); // in wh, but need ws
                energyRawTimeSeries.add(currentEnergyRate); // energy in use currently (this is really power!)
                double currentEnergyPerInterval = (currentEnergyRate / 3600) / 10; // NOT per second
                cumulativeEnergyScaled.getAndAdd(Math.round(currentEnergyPerInterval * Maths.SCALING_FACTOR));
                //System.out.println("currentEnergyRate = " + currentEnergyRate + " currentEnergyPerInterval = " + currentEnergyPerInterval + " cumulativeEnergyScaled = " + cumulativeEnergyScaled);
                hostEnergyRaw.put(host.getId(), energyRawTimeSeries);

                // energy sampling...
                List<Double> energyTimeSeries;
                if(hostEnergy.get(host.getId()) != null) {
                    energyTimeSeries = hostEnergy.get(host.getId());
                } else {
                    energyTimeSeries = new ArrayList<>();
                }
                //energyTimeSeries.add(Power.calculateEnergy(host, utilization)); // not that interesting, will match utilisation curve

                // instead calculate energy efficiency - work done, since last sample
                // deliberately NOT using app.totalAccumulatedMips - you can't only count completed cloudlets, or you end up with big sampling gaps

                // FIXME - this is slow and inefficient (repeated cloudlet looping of the same cloudlets)
                // adds 1 minute to the realworld sim time (was 2 seconds)
                // sometimes much more, over 2 minutes (because workload is higher)
                // 2 mins for 16% utilisation
                // 5.5 mins for 32% - can't use this code!
                /*
                BigInteger finishedSoFar = BigInteger.valueOf(0);
                if(host.getVmList() != null && host.getVmList().size() > 0) {
                    //System.out.println("CLOUDLET LIST SIZE : " + host.getVmList().get(0).getCloudletScheduler().getCloudletSubmittedList().size());
                    // can't be iterating over every cloudlet each time! but more cloudlets will have been submitted!
                    // am I looking for the utilisation of what has happened between samples
                    // or the running total?
                    // what do I do for utilisation? its current utilisation (since last sample) surely?
                    /*
                    for (Cloudlet c : host.getVmList().get(0).getCloudletScheduler().getCloudletSubmittedList()) {
                        finishedSoFar = finishedSoFar.add(BigInteger.valueOf(c.getFinishedLengthSoFar()));
                    }*//*
                    System.out.println("getCloudletExecList size : " + host.getVmList().get(0).getCloudletScheduler().getCloudletExecList().size());
                    for (CloudletExecution c : host.getVmList().get(0).getCloudletScheduler().getCloudletExecList()) { // what if it has finished!
                        finishedSoFar = finishedSoFar.add(BigInteger.valueOf(c.getCloudlet().getFinishedLengthSoFar()));
                    }
                    System.out.println("finishedSoFar : " + finishedSoFar);
                    // buggy as hell - if I want mips since last time, probably need to calcualte delta for each cloudlet
                    // rather than looking at finished length so far and deducting etc - error prone
                    // what I really want is simply a total accumulated (counted somewhere else)
                    // that is NOT complete, but all work done. then is this sampling loop I look at the delta.
                }
                BigInteger accumulatedMipsDelta = finishedSoFar.subtract(lastAccumulatedMips[0]);
                System.out.println("accumulatedMipsDelta : " + accumulatedMipsDelta);
                lastAccumulatedMips[0] = finishedSoFar;
                energyTimeSeries.add(Power.calculateEnergyEfficiency(host, utilization, accumulatedMipsDelta));
                *//*

                // performant method
                int hostID = (int) host.getId();
                BigInteger accumulatedMipsDelta = app.totalAccumulatedMiAll.subtract(lastAccumulatedMips[hostID]);
                lastAccumulatedMips[hostID] = app.totalAccumulatedMiAll;
                // pattern; big batches complete together, then large gaps. sometimes a single cloudlet by itself (1000)
                // is that perhaps interesting - does it demonstrate that computers are more efficient when you break up their work into small chunks?
                // no good looking at energy efficiency relative to current utilisation - everything might be utilised, but no work completing
                // note - its got to be work done, not completed - completed makes no sense and will be time-delayed
                energyTimeSeries.add(Power.calculateEnergyEfficiencyFromHost(host, accumulatedMipsDelta));

                hostEnergy.put(host.getId(), energyTimeSeries);

                // energy utilisation sampling...
                // work done (MIPS), per unit of energy required/consumed. cumulative.
                // this is what we are doing above!?

                // work done...
                List<Double> workDoneTimeSeries;
                if(hostWork.get(host.getId()) != null) {
                    workDoneTimeSeries = hostWork.get(host.getId());
                } else {
                    workDoneTimeSeries = new ArrayList<>();
                }
                workDoneTimeSeries.add(accumulatedMipsDelta.doubleValue());
                hostWork.put(host.getId(), workDoneTimeSeries);

                // work done (cumulative)...
                List<Double> workDoneCumTimeSeries;
                if(hostWorkCumulative.get(host.getId()) != null) {
                    workDoneCumTimeSeries = hostWorkCumulative.get(host.getId());
                } else {
                    workDoneCumTimeSeries = new ArrayList<>();
                }
                workDoneCumTimeSeries.add(app.totalAccumulatedMiAll.doubleValue());
                hostWorkCumulative.put(host.getId(), workDoneCumTimeSeries);

                // all hosts can appear utilised when they aren't (i.e. because they are simply allocated!) - this doesn't meet my definition of utilisation;
                // allocation is not what we are typically interested in - it's not CPU utilisation under load
                //LOGGER.info("Time {}: Host {} CPU Utilisation: {}", simulation.clock(), host.getId(), utilisation * 100);
                // FIXME let's re-introduce a custom utilisation measure
                 // work done since we last calculated, using the theoretical max
                //LOGGER.info("Work Done {}", (accumulatedMipsDelta.doubleValue() / 100));
                //LOGGER.info("Work MAX {}", (TICK_INTERVAL * 1000 * 16));
                utilisation = (accumulatedMipsDelta.doubleValue() / datacenter.getHostList().size())  / (TICK_INTERVAL * 1000 * 16);
                //LOGGER.info("Time {}: Host {} CPU Utilisation (NEW): {}", simulation.clock(), host.getId(), utilisation * 100);

                double sum = 0;
                if (sumUtil.get(host.getId()) != null) {
                    sum = sumUtil.get(host.getId()) + utilisation;
                }
                int cnt = 0;
                if (cntUtil.get(host.getId()) != null) {
                    cnt = cntUtil.get(host.getId()) + 1;
                }
                sumUtil.put(host.getId(), sum);
                cntUtil.put(host.getId(), cnt);

                // fixing utilisation numbers (using the correct value!)
                List<Double> utilTimeSeries;
                if(hostUtil.get(host.getId()) != null) {
                    utilTimeSeries = hostUtil.get(host.getId());
                } else {
                    utilTimeSeries = new ArrayList<>();
                }
                utilTimeSeries.add(utilisation * 100);
                hostUtil.put(host.getId(), utilTimeSeries);

                // SCI sampling
                List<Double> sciTimeSeries;
                if(hostSci.get(host.getId()) != null) {
                    sciTimeSeries = hostSci.get(host.getId());
                } else {
                    sciTimeSeries = new ArrayList<>();
                }

                List<Double> sciCumTimeSeries;
                if(hostSciCumulative.get(host.getId()) != null) {
                    sciCumTimeSeries = hostSciCumulative.get(host.getId());
                } else {
                    sciCumTimeSeries = new ArrayList<>();
                }

                double cumulativeEnergyDescaled = cumulativeEnergyScaled.get() / 1_000_000.0; // funky maths is because I'm using a scaled AtomicLong!
                double operationalCumulative = Carbon.energyToCarbon(cumulativeEnergyDescaled); // operational emissions per sampling interval! cumulative

                double operational = Carbon.energyToCarbon(currentEnergyPerInterval);

                double embodiedTotal = 0; // FIXME, for each per server value, use the total embodied for all servers (fudge so that I can only use 1 host for the time series)
                for (Host hostNested : datacenter.getHostList()) {
                    HostSimpleFixed hsf = (HostSimpleFixed) hostNested;
                    embodiedTotal += hsf.embodiedEmissions;
                }
                // amortize embodied emissions;
                // these are lifetime embodied emissions numbers (raw)
                // they need to be amortized - shred to the same time-frame as the operational emissions (currently 1hr)
                // assuming 4 years of operational lifetime...
                embodiedTotal = Sci.amortizeEmbodiedToSample(embodiedTotal);
                cumulativeEmbodied.getAndAdd(Math.round(embodiedTotal * Maths.SCALING_FACTOR));

                // NOTE we are calculating TOTAL SCI, for ALL servers, each time (not per server)

                sciTimeSeries.add(Sci.calculateSci(operational, embodiedTotal, accumulatedMipsDelta.doubleValue(), false));
                hostSci.put(host.getId(), sciTimeSeries);

                operationalCumulative = operationalCumulative * datacenter.getHostList().size();
                double cumulativeEmbodiedTotal = (cumulativeEmbodied.get() / (double) Maths.SCALING_FACTOR); // this is already for all servers! * datacenter.getHostList().size();

                sciCumTimeSeries.add(Sci.calculateSci(operationalCumulative, cumulativeEmbodiedTotal, app.totalAccumulatedMiAll.doubleValue(), false));
                hostSciCumulative.put(host.getId(), sciCumTimeSeries);

                /*
                long endTick = Math.round(END_TIME / TICK_INTERVAL);
                if(tickCount.get() >= endTick) {
                    System.err.println("operationalCumulative : " + operationalCumulative);
                    System.err.println("cumulativeEmbodied : " + cumulativeEmbodiedTotal);

                    // although we are printing these per host, they not a per host value!
                    System.err.println("totalAccumulatedMiAll : " + app.totalAccumulatedMiAll);
                    System.err.println("totalAccumulatedMi : " + app.totalAccumulatedMi);
                }
                *//*
            }

            // generate web workloads (now insider sampler, for better control. can also log/chart easier - FIXME move to above logic, rather than std out)
            double time = simulation.clock();
            List<Cloudlet> newCloudlets = app.generateWorkloadAtTime(time, vmList);
            if (!newCloudlets.isEmpty()) {
                pendingCloudlets.addAll(newCloudlets);
            }
            submitReadyCloudlets.run();
        });*/


//        AtomicInteger tickCount = new AtomicInteger();