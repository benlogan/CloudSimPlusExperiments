package com.loganbe.utilities;

import com.loganbe.application.AbstractAppModel;
import com.loganbe.power.Carbon;
import com.loganbe.power.Power;
import com.loganbe.power.Sci;
import com.loganbe.SimulationConfig;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.schedulers.cloudlet.CustomCloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SamplingMetrics {

    public static final Logger LOGGER = LoggerFactory.getLogger(SamplingMetrics.class.getSimpleName());

    private final CloudSimPlus simulation;
    private final Datacenter datacenter;
    private final AbstractAppModel app;
    private final DatacenterBroker broker;

    // sampling interval and end time - set these to the underlying sim variables governing sim duration
    private final double tickInterval; // e.g. 0.1 = every 1/10th second - expect 36,000 readings for the normal 1 hr simulation
    private final double endTime; // note this is not in ticks, but in time!

    // time series data structures used in sampling, for export/charting

    private final Map<Long, List<Double>> hostWork = new HashMap<>();               // work done (MI's), since last sample
    private final Map<Long, List<Double>> hostWorkCumulative = new HashMap<>();     // work done (MI's), running total

    private final Map<Long, List<Double>> hostUtil = new HashMap<>();               // using the custom methodology (work done)
    private final Map<Long, List<Double>> hostUtilAllocation = new HashMap<>();     // using the default framework methodology (allocation based)

    private final Map<Long, List<Double>> hostEnergy = new HashMap<>();             // current hourly energy rate (power) - NOT energy consumed since last sample (that would be much smaller)
    private final Map<Long, Double> hostEnergyCumulative = new HashMap<>();         // one value (per host), not a time series
    private final Map<Long, Double> hostEmbodiedCumulative = new HashMap<>();       // one value (per host), not a time series

    private final Map<Long, List<Double>> hostSci = new HashMap<>();                // point-in-time SCI
    private final Map<Long, List<Double>> hostSciCumulative = new HashMap<>();      // cumulative SCI (not point in time) - should converge with average for the sim run

    private final Map<Long, BigInteger> lastAccumulatedMips = new HashMap<>();

    private WorkloadGenerator workloadGenerator;

    private double lastCloudletLifecycleLogTime = Double.NEGATIVE_INFINITY;

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
        this.broker = broker;
        this.app = app;
        this.tickInterval = tickInterval;
        this.endTime = endTime;
        this.workloadGenerator = new WorkloadGenerator(app, vmList, broker, new ArrayList<>(initialCloudlets));

        LOGGER.info("Using broker implementation: {}", broker.getClass().getName());

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

        if (SimulationConfig.LOG_CLOUDLET_LIFECYCLE_DIAGNOSTICS
                && time - lastCloudletLifecycleLogTime >= SimulationConfig.CLOUDLET_LIFECYCLE_DIAGNOSTICS_INTERVAL) {
            //logCloudletLifecycleSummary(time); // useful, but chatty
            lastCloudletLifecycleLogTime = time;
        }
    }

    private void logCloudletLifecycleSummary(final double time) {
        int schedulerCloudlets = 0;
        int schedulerExecCloudlets = 0;
        int schedulerFinishedCloudlets = 0;
        int schedulerSubmittedCloudlets = 0;
        int schedulerReturnedCloudlets = 0;

        for (final Host host : datacenter.getHostList()) {
            for (final var vm : host.getVmList()) {
                schedulerCloudlets += vm.getCloudletScheduler().getCloudletList().size();
                schedulerExecCloudlets += vm.getCloudletScheduler().getCloudletExecList().size();
                schedulerFinishedCloudlets += vm.getCloudletScheduler().getCloudletFinishedList().size();
                if (vm.getCloudletScheduler().isCloudletSubmittedListEnabled()) {
                    schedulerSubmittedCloudlets += vm.getCloudletScheduler().getCloudletSubmittedList().size();
                }
                if (vm.getCloudletScheduler() instanceof CustomCloudletScheduler customScheduler) {
                    schedulerReturnedCloudlets += customScheduler.getReturnedCloudletCount();
                }
            }
        }

        final var created = app.cloudletCreatedCounter;
        final var finished = app.cloudletCounter;
        final var approxInFlight = created.subtract(finished);
        final long gcRegistered = app.cloudletGcTracker.getRegisteredCount();
        final long gcCollected = app.cloudletGcTracker.getCollectedCount();
        final long gcOutstanding = app.cloudletGcTracker.getOutstandingCount();
        final int brokerWaitingCloudlets = broker.getCloudletWaitingList().size();
        final int brokerFinishedCloudlets = broker.getCloudletFinishedList().size();
        final int brokerSubmittedCloudlets = broker.getCloudletSubmittedList().size();
        final int brokerCreatedCloudlets = broker.getCloudletCreatedList().size();

        LOGGER.info(
                "Cloudlet lifecycle @ {}s: created={}, finished={}, approxInFlight={}, gcRegistered={}, gcCollected={}, gcOutstanding={}, brokerWaiting={}, brokerSubmitted={}, brokerCreated={}, brokerFinished={}, schedulerCloudlets={}, schedulerExecCloudlets={}, schedulerFinished={}, schedulerSubmitted={}, schedulerReturned={}",
                Maths.scaleAndRound(time), created, finished, approxInFlight, gcRegistered, gcCollected, gcOutstanding,
                brokerWaitingCloudlets, brokerSubmittedCloudlets, brokerCreatedCloudlets, brokerFinishedCloudlets,
                schedulerCloudlets, schedulerExecCloudlets, schedulerFinishedCloudlets, schedulerSubmittedCloudlets, schedulerReturnedCloudlets);
    }

    private void sampleHostMetrics() {
        for (final Host host : datacenter.getHostList()) {
            if(hostEnergyCumulative.get(host.getId()) == null) {
                hostEnergyCumulative.put(host.getId(), 0.0);
            }
            if(hostEmbodiedCumulative.get(host.getId()) == null) {
                hostEmbodiedCumulative.put(host.getId(), 0.0);
            }

            final long hostId = host.getId();

            // important note - cumulative values are generally for all hosts (not just current host)
            // useful because we only write the data for host=0

            // WORK DONE

            final BigInteger accumulatedWorkDelta = getAccumulatedMipsDelta(hostId, datacenter.getHostList().size());

            getOrCreateSeries(hostWork, hostId).add(accumulatedWorkDelta.doubleValue());
            getOrCreateSeries(hostWorkCumulative, hostId).add(app.totalAccumulatedMiAll.doubleValue());

            // UTILISATION

            //final double utilisationByAllocation = host.getCpuPercentUtilization(); // using the default framework approach (can be unreliable)
            //getOrCreateSeries(hostUtilAllocation, hostId).add(utilisationByAllocation * 100);

            // Calculate utilisation as: (work done per host) / (host capacity in this interval)
            // accumulatedWorkDelta = total work across all hosts in this sampling interval
            // workPerHost = average work per host (total work / number of hosts)
            // hostCapacityInInterval = host's total MIPS capacity * tickInterval
            final double workPerHost = accumulatedWorkDelta.doubleValue(); // THIS IS ALREADY PER HOST!

            // Calculate actual host capacity from its PEs
            final int hostCores = Math.toIntExact(host.getPesNumber());
            //final double hostMipsPerPe = host.getPeList().isEmpty() ? 1000.0 : host.getPeList().get(0).getCapacity();
            final double hostMipsPerPe = 1000; // FIXME source this properly!
            final double hostCapacityPerSecond = hostCores * hostMipsPerPe;
            final double hostCapacityInInterval = hostCapacityPerSecond * tickInterval;

            double utilisationByWorkDone = workPerHost / hostCapacityInInterval;
            // Clamp to [0, 1] to avoid invalid percentages
            utilisationByWorkDone = Math.max(0.0, Math.min(1.0, utilisationByWorkDone));
            getOrCreateSeries(hostUtil, hostId).add(utilisationByWorkDone * 100);

            if(utilisationByWorkDone < 0 || utilisationByWorkDone > 1) {
                LOGGER.warn("Host {}: Clamped utilisationByWorkDone (original: {}, workPerHost={}, hostCapacity={}, hostCores={}, hostMips={}, tickInterval={})",
                        hostId, workPerHost / hostCapacityInInterval, workPerHost, hostCapacityInInterval, hostCores, hostMipsPerPe, tickInterval);
            }

            // ENERGY

            final double currentEnergyRate = Power.calculateEnergy(host, utilisationByWorkDone);
            getOrCreateSeries(hostEnergy, hostId).add(currentEnergyRate);

            /*
            if(hostId == 0) {
                if (Integer.remainderUnsigned(getOrCreateSeries(hostEnergy, hostId).size(), 100) == 0) {
                    System.out.println("hostEnergy has grown by 100. size = " + getOrCreateSeries(hostEnergy, hostId).size());
                }
            }*/

            // Power.calculateEnergyEfficiencyFromHost(host, accumulatedWorkDelta); - not used at the moment

            final double currentEnergyPerInterval = (currentEnergyRate / 3600) / 1000; // convert from Wh to kWh for the interval
            double cumulativeEnergy = hostEnergyCumulative.get(hostId) + currentEnergyPerInterval;
            hostEnergyCumulative.put(hostId, cumulativeEnergy);

            // SCI (point-in-time - across the interval)
            // important - this will never match the cumulative SCI, or the final overall SCI
            // because we will sample highly utilised intervals (so SCI will be lower)
            // and intervals where no work is completed (so SCI will hit 1)
            // so the cumulative SCI is a better representation of the overall SCI for the sim run
            final List<Double> sciTimeSeries = getOrCreateSeries(hostSci, hostId);

            final double operational = Carbon.energyToCarbon(currentEnergyPerInterval);

            final HostSimpleFixed hsf = (HostSimpleFixed) host;
            final double embodied = calculateEmbodiedEmissions(hsf.embodiedEmissions);

            final double rate = accumulatedWorkDelta.doubleValue();

            sciTimeSeries.add(Sci.calculateSci(operational, embodied, rate, false));

            // SCI (cumulative and one host only)
            final List<Double> sciCumTimeSeries = getOrCreateSeries(hostSciCumulative, hostId);

            final double cumulativeEnergyDescaled = cumulativeEnergy;

            double operationalCumulative = Carbon.energyToCarbon(cumulativeEnergyDescaled);

            double embodiedCumulative = hostEmbodiedCumulative.get(hostId) + embodied;
            hostEmbodiedCumulative.put(hostId, embodiedCumulative);

            final double rateCumulative = app.totalAccumulatedMiAll.doubleValue() / datacenter.getHostList().size();

            sciCumTimeSeries.add(Sci.calculateSci(operationalCumulative, embodiedCumulative, rateCumulative, false));
        }
    }

    private BigInteger getAccumulatedMipsDelta(final long hostId, final int hostCount) {
        final BigInteger current = app.totalAccumulatedMiAll.divide(BigInteger.valueOf(hostCount));
        final BigInteger previous = lastAccumulatedMips.getOrDefault(hostId, BigInteger.ZERO);
        lastAccumulatedMips.put(hostId, current);
        return current.subtract(previous);
    }

    private double calculateEmbodiedEmissions(double embodiedEmissions) {
        return Sci.amortizeEmbodiedToSample(embodiedEmissions);
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