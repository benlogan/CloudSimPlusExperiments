package com.loganbe;

import ch.qos.logback.classic.Level;
import com.loganbe.application.AbstractAppModel;
import com.loganbe.application.BatchApp;
import com.loganbe.application.WebApp;
import com.loganbe.interventions.InterventionSuite;
import com.loganbe.power.Carbon;
import com.loganbe.power.Power;
import com.loganbe.power.Sci;
import com.loganbe.templates.ServersSpecification;
import com.loganbe.templates.SimSpecFromFileLegacy;
import com.loganbe.templates.SimSpecInterfaceHomogenous;
import com.loganbe.utilities.*;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.loganbe.SimulationConfig.*;

public class Main {

    private CloudSimPlus simulation;

    public Datacenter datacenter;
    private DatacenterBroker broker;

    public List<CustomVm> vmList;
    private List<Cloudlet> cloudletList;

    private SimSpecFromFileLegacy simSpec = new SimSpecFromFileLegacy("data/infra_templates/scenarioA.yaml");
    //private SimSpecFromFile simSpec = new SimSpecFromFile("data/infra_templates/example.yaml");

    final static int SIM_COUNT = 1;     // how many sim iterations
    private int simCounter = 1;         // how many discrete simulations have completed

    // used only for comparisons across multiple simulation runs
    private Map<Integer, Double> energyMap = new HashMap<>();
    private Map<Integer, Double> workMap = new HashMap<>();

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class.getSimpleName());

    public static void main(String[] args) {
        Main main = new Main();

        for(int i = 0; i < SIM_COUNT; i++) {
            main.runSimulation(null);
            //main.runSimulation(new InterventionSuite());
        }

        if (SIM_COUNT > 1) {
            DeltaHelper.calculateDeltas(main.energyMap, main.workMap);
        }
    }

    private void runSimulation(InterventionSuite interventions) {
        long currentTime = System.currentTimeMillis();

        // enables some level of log messages. Make sure to import org.cloudsimplus.util.Log
        Log.setLevel(Level.INFO); // THERE IS NO DEBUG LOGGING (AND ONLY MINIMAL TRACE)!

        LOGGER.info("Using Sim Config File : file://{}", new File(simSpec.getFilename()).getAbsolutePath()); // use of file:// here is simply to make the link clickable in console

        simulation = new CloudSimPlus(MIN_TIME_BETWEEN_EVENTS); // trying to ensure all events are processed, without any misses

        if(DURATION > 0) {
            simulation.terminateAt(DURATION);
        }

        datacenter = InfraCreationHelper.createDatacenter(simSpec, simulation);

        // creates a broker; software acting on behalf of the cloud customer to manage their VMs & Cloudlets
        broker = new DatacenterBrokerSimple(simulation);

        // likely need to experiment here later - with different scheduling/allocation strategies
        // perhaps one that creates new cloudlets when others finish - simulating open-ended execution
        // or just make them long life - is that not more realistic of the enterprise?

        //broker.setVmDestructionDelay(100); // doesn't necessarily result in VM's hanging around to complete unfinished cloudlets!

        if (SimSpecInterfaceHomogenous.class.isAssignableFrom(simSpec.getClass())) {
            vmList = InfraCreationHelper.createVms(simSpec);
        } else {
            vmList = InfraCreationHelper.createVmsFromHost(simSpec);
        }

        // old/simple method (legacy approach, pre app abstraction model)
        //cloudletList = CloudletHelper.createCloudlets(simSpec, simSpec.getCloudletSpecification().getCloudlets(), vmList);

        // create a list of cloudlets (cloud applications), using the abstraction model;
        AbstractAppModel app;
        LOGGER.info("App Type : {}", simSpec.getApplicationType());
        if(simSpec.getApplicationType().equals("WEB")) {
            app = new WebApp(simSpec.getCloudletSpecification().getCloudlet_length(), simSpec.getWebAppSpecification().getArrival_interval(), simSpec.getCloudletSpecification().getCloudlet_pes());
        } else if(simSpec.getApplicationType().equals("BATCH")) {
            app = new BatchApp(simSpec.getCloudletSpecification().getCloudlet_length(), simSpec.getBatchAppSpecification().getCloudlet_count(), simSpec.getCloudletSpecification().getCloudlet_pes());
        } else {
            app = null;
        }

        cloudletList = app.generateInitialWorkload(vmList);
        // using the new cloudlet abstraction code under application.*
        // new code is quite different - it's lots of small cloudlets running sequentially (and periodically)
        // old code is one big (never ending) cloudlet (need to refresh memory on how that's implemented, but its definitely one cloudlet)
        // results won't be exactly the same - there will be gaps etc with the new approach
        // remember that later you need to periodically add new cloudlets (in the webapp scenario) - you start with nothing!

        broker.submitVmList(vmList);

        // at t=0, workload generation may run before VM_CREATE_ACK events are processed.
        // keep cloudlets in a pending queue and submit only when their target VM exists,
        // avoiding broker postponement warnings caused by startup event ordering.
        final List<Cloudlet> pendingCloudlets = new ArrayList<>(cloudletList);
        final Runnable submitReadyCloudlets = () -> {
            if (pendingCloudlets.isEmpty()) {
                return;
            }

            final List<Cloudlet> ready = new ArrayList<>();
            final List<Vm> createdVms = broker.getVmCreatedList();
            for (final Cloudlet cloudlet : pendingCloudlets) {
                final Vm targetVm = cloudlet.getVm();
                final boolean canSubmit = targetVm == Vm.NULL ? !createdVms.isEmpty() : createdVms.contains(targetVm);
                if (canSubmit) {
                    ready.add(cloudlet);
                }
            }

            if (!ready.isEmpty()) {
                pendingCloudlets.removeAll(ready);
                broker.submitCloudletList(ready);
            }
        };

        LOGGER.info("getSchedulingInterval : {}s", datacenter.getSchedulingInterval());

        LOGGER.info("getMinTimeBetweenEvents : {}s", simulation.getMinTimeBetweenEvents());
        // defaults to 0.1s, presumably needs to be very small if we are going with small intervals
        // otherwise events will be missed!

        // Add a listener for every event processed
        /*
        simulation.addOnEventProcessingListener(event -> {
            LOGGER.trace("EVENT : " + event);

            // RELEVANT TAGS (CloudSimTag)
            event flow appears to be roughly in this order;
             DC_REGISTRATION_REQUEST = 2
             DC_LIST_REQUEST = 4
             VM_CREATE_ACK = 32
             CLOUDLET_CREATION = 14
             CLOUDLET_SUBMIT = 16
             VM_UPDATE_CLOUDLET_PROCESSING = 41
        });
        simulation.addOnClockTickListener(info -> {
            LOGGER.trace("-------------------------------------------------------");
            LOGGER.trace("CLOCK TICK (START): time = " + info.getTime());
            for (Host host : datacenter.getHostList()) {
                for (Vm vm : host.getVmList()) {
                    LOGGER.trace("VM : " + vm.getId() + " : Running Cloudlets = " + vm.getCloudletScheduler().getCloudletList().size());
                    for (Cloudlet cl : vm.getCloudletScheduler().getCloudletList()) {
                        LOGGER.trace(cl + " : " + cl.getStatus() + " : Execution Time = " + cl.getTotalExecutionTime() + " : Progress = " + cl.getFinishedLengthSoFar());
                    }
                }
            }
            LOGGER.trace("CLOCK TICK (END)");
            LOGGER.trace("-------------------------------------------------------");
        });*/

        // custom utilisation logic - essentially sampling utilisation more frequently and taking my own average
        // more granular and much more accurate!
        Map<Long, Double> sumUtil = new HashMap<>();
        Map<Long, Integer> cntUtil = new HashMap<>();

        // time series data structures used in sampling, for export/charting
        Map<Long, List> hostUtil = new HashMap<>();
        Map<Long, List> hostEnergyRaw = new HashMap<>(); // current hourly energy rate (power) - NOT energy consumed since last sample (that would be much smaller)
        Map<Long, List> hostEnergy = new HashMap<>();
        Map<Long, List> hostWork = new HashMap<>();
        Map<Long, List> hostWorkCumulative = new HashMap<>();
        Map<Long, List> hostSci = new HashMap<>();
        Map<Long, List> hostSciCumulative = new HashMap<>(); // cumulative SCI (not point in time) - should converge with average for the sim run

        AtomicLong cumulativeEnergyScaled = new AtomicLong();
        AtomicLong cumulativeEmbodied = new AtomicLong();
        final BigInteger[] lastAccumulatedMips = {BigInteger.valueOf(0)};

        // sampling interval and end time - set these to the underlying sim variables governing sim duration
        final double TICK_INTERVAL = SCHEDULING_INTERVAL; // 0.1 = every 1/10th second - expect 36,000 readings for the normal 1 hr simulation
        final double END_TIME = DURATION; // note this is not in ticks, but in time!

        AtomicInteger tickCount = new AtomicInteger();
        // used to do this via addOnClockTickListener, but that is NOT consistent
        // you will get a variable number of events, with random load
        // better to use a custom sampler at a fixed interval - for charting etc
        new Sampler(simulation, TICK_INTERVAL, END_TIME, t -> {
            tickCount.getAndIncrement();

            for (Host host : datacenter.getHostList()) {

                // CPU utilisation sampling...
                double utilisation = host.getCpuPercentUtilization();
                List<Double> utilTimeSeries;
                if(hostUtil.get(host.getId()) != null) {
                    utilTimeSeries = hostUtil.get(host.getId());
                } else {
                    utilTimeSeries = new ArrayList<>();
                }
                utilTimeSeries.add(utilisation * 100);
                hostUtil.put(host.getId(), utilTimeSeries);

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
                */

                // new performant method
                BigInteger accumulatedMipsDelta = app.totalAccumulatedMiAll.subtract(lastAccumulatedMips[0]);
                lastAccumulatedMips[0] = app.totalAccumulatedMiAll;
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
                //System.out.printf("Time %.2f: Host %d CPU Utilization: %.2f%%\n", simulation.clock(), host.getId(), utilization * 100);

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
                */
            }

            // generate web workloads (now insider sampler, for better control. can also log/chart easier - FIXME move to above logic, rather than std out)
            double time = simulation.clock();
            List<Cloudlet> newCloudlets = app.generateWorkloadAtTime(time, vmList);
            if (!newCloudlets.isEmpty()) {
                pendingCloudlets.addAll(newCloudlets);
            }
            submitReadyCloudlets.run();
        });

        // periodically check for new workload (e.g. for web apps, additional cloudlets are added during sim execution)
        // this used to be outside the sampling loop, but I'd rather it run every 'step'
        /*
        simulation.addOnClockTickListener(evt -> {
            double time = simulation.clock();
            List<Cloudlet> newCloudlets = app.generateWorkloadAtTime(time, vmList);
            if (!newCloudlets.isEmpty()) {
                broker.submitCloudletList(newCloudlets);
            }
        });*/

        //simulation.terminateAt(10000); // won't make any difference if you have unfinished cloudlets! (because the events have probably already been processed)

        // immediately before we start the sim, apply any interventions;
        if(interventions != null) {
            interventions.applyInterventions(this);
        }
        simulation.start();

        int incomplete = broker.getCloudletSubmittedList().size() - broker.getCloudletFinishedList().size();
        if (incomplete > 0) {
            LOGGER.error("Some Cloudlets Remain Unfinished : " + incomplete);
            long incompleteMI = 0;
            for(Cloudlet cloudlet : broker.getCloudletSubmittedList()) {
                if(cloudlet.isRunning()) {
                    //LOGGER.error("CLOUDLET STILL RUNNING : " + cloudlet.getId());
                    //LOGGER.error("CLOUDLET FINISHED SO FAR : " + cloudlet.getFinishedLengthSoFar());
                    incompleteMI += cloudlet.getFinishedLengthSoFar();
                }
            }
            LOGGER.error("MI associated with incomplete cloudlets (in flight) : " + incompleteMI);
            // FIXME in theory this should be counted towards 'work completed', but its usually small
        }

        LOGGER.info("Simulation End Time " + Maths.scaleAndRound(simulation.clockInHours()) + "h or " + Maths.scaleAndRound(simulation.clockInMinutes()) + "m");
        //System.err.println("tickCount (sampling) " + tickCount.get()); // maps roughly to scheduling interval

        // simulation complete - calculate work done...

        //long totalWorkExpected;
        // work expected is a function of how long we run the simulation for (not pre-determined)
        long totalWorkExpectedMax; // theoretical max, if you used all cores

        if (SimSpecInterfaceHomogenous.class.isAssignableFrom(simSpec.getClass())) { // legacy template
            // important - not theoretical max, but rather how many cores did you ask to use (in configuration)!
            //totalWorkExpected = 1L * simSpec.getHostSpecification().getHost_mips() * simSpec.getHostSpecification().getHosts() * simSpec.getCloudletSpecification().getCloudlet_pes() * SimulationConfig.DURATION; // legacy approach (homogenous)
            totalWorkExpectedMax = 1L * simSpec.getHostSpecification().getHost_mips() * simSpec.getHostSpecification().getHosts() * simSpec.getHostSpecification().getHost_pes() * DURATION;
        } else {
            //totalWorkExpected = 0;
            for (ServersSpecification server : simSpec.getServerSpecifications()) {
                int mips = ServersSpecification.calculateMips(server.getSpeed()) * simSpec.getCloudletSpecification().getCloudlet_pes(); // how many do you choose to use, not how many cores are there (server.getCpu())
                //totalWorkExpected += mips;
            }
            //totalWorkExpected = totalWorkExpected * SimulationConfig.DURATION;
            totalWorkExpectedMax = 0; // FIXME later
        }
        // FIXME - not accounting for interventions! Not a major issue, just ignore the warning...
        //LOGGER.info(totalWorkExpected + " = Total Work Expected (MI)");
        LOGGER.info(totalWorkExpectedMax + " = Total Work Expected (MI)"); // used to call this MAX

        // this is based on completing cloudlets incrementing a work completed counter, using the cloudlet length, from the cloudlet specification
        // disabling for now, just noise - they should all be the same!
        //LOGGER.info(app.totalAccumulatedMips + " = Total Work Completed (MI)");
        //LOGGER.info(app.totalAccumulatedMipsAll + " = Total Work Completed - NEW (MI)");

        // this is based on the actual completed cloudlet length (not expected or specified), so should be closer to the truth!
        BigInteger actualAccumulatedMips = new BigInteger(String.valueOf(0)); // TOTAL length, across ALL cores
        for (Cloudlet cloudlet : broker.getCloudletFinishedList()) {
            actualAccumulatedMips = actualAccumulatedMips.add(BigInteger.valueOf(cloudlet.getTotalLength()));
        }
        LOGGER.info(actualAccumulatedMips + " = Actual Work Completed (MI), for " + broker.getCloudletFinishedList().size() + " finished cloudlets");

        //calculateWorkDelta(totalWorkExpected, totalWorkExpectedMax, actualAccumulatedMips);
        DeltaHelper.calculateWorkDelta(totalWorkExpectedMax, actualAccumulatedMips);

        if(DURATION == -1) { // don't bother calculating this - usually using a fixed time frame
            DeltaHelper.calculateTimeDelta(simSpec, simulation, totalWorkExpectedMax);
        }

        // process / output / visualise results...

        //final var cloudletFinishedList = broker.getCloudletCreatedList(); // safer than getCloudletFinishedList

        // results table to stdout
        //new CloudletsTableBuilderExtended(cloudletFinishedList).build(); // customised version with fixes/enhancements

        //new CloudletsTableBuilder(cloudletFinishedList, new HtmlTable()).build();
        //new CloudletsTableBuilder(cloudletFinishedList, new CsvTable()).build(); // replaced/extended below

        SimpleDateFormat formatter = new SimpleDateFormat("dd_MM_yyyy_HH:mm:ss");
        String friendlyDate = formatter.format(new Date());
        // results table to csv, for easy charting (new custom table type, to fetch csv as string)
        /*
        TableBuilderAbstract tableBuilder = new CloudletsTableBuilder(cloudletFinishedList, new ExportCsvTable());
        ExportCsvTable table = (ExportCsvTable) tableBuilder.getTable();
        tableBuilder.build();
        Utilities.writeCsv(table.getCsvString(), "data/sim_data_" + friendlyDate + ".csv");
        */
        // the call to table.getCsvString() does NOT scale well, for large numbers of cloudlets - disabling for now

        // new table showing activity in each core
        //System.out.println(broker.getVmCreatedList().get(0).getHost().getPeList().get(0).getStatus().toString());

        // after the simulation, print the cloudlet-to-vCPU mapping
        // the basic idea is to manually maintain a mapping of cloudlets to cores (because CloudSimPlus doesn't really do that)
        // then we can see exactly how work is being distributed
        // useful because the usual table doesn't tell us what is going on in each core (so this complements the cloudlet view/table)

        //List<CustomVm> vmList = broker.getVmCreatedList();
        //CloudletPrinter.printCloudletExecutionStatsPerVcpu(broker, vmList);
        //CloudletPrinter.printCloudletExecutionStatsPerVcpuTabular(broker, vmList);

        // custom utilisation logic...
        Map<Long, Double> hostUtilisation = new HashMap<>();
        for (Host host : datacenter.getHostList()) {
            double sum = sumUtil.get(host.getId());
            int count = cntUtil.get(host.getId());
            double averageUtilisation = sum / count;
            //LOGGER.info("HOST : " + host.getId() + " AVG UTILISATION : " + (averageUtilisation * 100) + "%");
            //LOGGER.info("Utilisation (old method) : " + (host.getCpuUtilizationStats().getMean() * 100) + "%");
            // old method - for a webapp, when all cores are utilised this will appear to be 100%, incorrectly
            // when only one core is utilised, it will simply show the % of one core, as if that one core was being fully utilised (also wrong)
            // for a batch app, it will actually be correct and match the custom metric (both single and multithreaded)
            // this sort of makes sense - if we are starting and stopping jobs more often - we need to take more granular measurements to ensure accuracy
            hostUtilisation.put(host.getId(), averageUtilisation);
            // FIXME - not doing anything with VM utilisation, it should match host for most of my scenarios (can revisit this later)
        }

        //exportTimeSeriesData(hostEnergy, "energy util", "data/sim_data_energy_util_" + friendlyDate + ".csv");
        ExportTimeSeriesCsv.exportTimeSeriesData(hostEnergyRaw, null, "energy",  friendlyDate + "/sim_data_energy_" + friendlyDate);
        ExportTimeSeriesCsv.exportTimeSeriesData(hostUtil, null, "utilisation", friendlyDate + "/sim_data_util_" + friendlyDate);
        ExportTimeSeriesCsv.exportTimeSeriesData(hostWork, hostWorkCumulative, "work", friendlyDate + "/sim_data_work_" + friendlyDate);
        ExportTimeSeriesCsv.exportTimeSeriesData(hostSci, hostSciCumulative, "sci",friendlyDate + "/sim_data_sci_" + friendlyDate);

        // used for comparing across sim runs only...
        double totalEnergy = Power.calculateTotalEnergy(datacenter.getHostList(), hostUtilisation, actualAccumulatedMips);
        energyMap.put(simCounter, totalEnergy);
        workMap.put(simCounter, actualAccumulatedMips.doubleValue());

        simCounter++;

        currentTime = System.currentTimeMillis() - currentTime;
        LOGGER.info("Simulation Elapsed Time (real world) = " + currentTime/1000 + "s");
    }

}