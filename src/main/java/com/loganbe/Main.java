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
import com.loganbe.utilities.Maths;
import com.loganbe.utilities.Sampler;
import com.loganbe.utilities.Utilities;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.loganbe.SimulationConfig.ACCEPTABLE_WORKLOAD_ERROR;

public class Main {

    private CloudSimPlus simulation;
    private DatacenterBroker broker;
    public List<Host> hostList;
    public List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter;

    //private SimSpecFromFileLegacy simSpec = new SimSpecFromFileLegacy("data/infra_templates/big_company.yaml");
    private SimSpecFromFileLegacy simSpec = new SimSpecFromFileLegacy("data/infra_templates/scenarioB.yaml");
    //private SimSpecFromFile simSpec = new SimSpecFromFile("data/infra_templates/example.yaml");

    private int simCount = 1; // how many discrete simulations have completed, NOT how many you want to run
    private Map<Integer, Double> energyMap = new HashMap();
    private Map<Integer, Double> workMap = new HashMap();
    private Map<Integer, List<Double>> chartMap = new HashMap();

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class.getSimpleName());

    public static void main(String[] args) {
        Main main = new Main();

        int intendedExecutions = 1;

        for(int i = 0; i < intendedExecutions; i++) {
            main.runSimulation(null);
        }

        // multiple sim runs...
        //main.runSimulation(new InterventionSuite());
        //main.printEnergy();
    }

    // FIXME need a results object for sim executions and a comparator function
    private void printEnergy() {
        for(int i = 1; i < simCount; i++) {
            System.out.println("Sim Run : " + i + " energy : " + energyMap.get(i) + "Wh");
            System.out.println("Sim Run : " + i + " work : " + workMap.get(i) + "(MIPS)");
        }
        double energySaving = energyMap.get(1) - energyMap.get(2);

        System.out.printf("Energy Delta (saving), when applying interventions during a 2nd sim run : %.2f Wh (or %.2f %% less energy consumed)\n",
                energySaving, (energySaving / energyMap.get(1))*100);

        double workDifference = workMap.get(2) - workMap.get(1);
        System.out.printf("Work Delta (boost), when applying interventions during a 2nd sim run : %.2f MIPS (or %.2f %% more work done)\n",
                workDifference, (workDifference / workMap.get(1))*100);

        // new charting friendly output (web app)
        System.out.println("--------------CHARTING------------->");
        for(int i = 1; i < simCount; i++) {
            for (Double n : chartMap.get(i)) {
                System.out.print(n + "\t");  // tab between numbers (for easy copy into Excel)
            }
            System.out.println();
        }
        System.out.println("<-------------CHARTING--------------");
    }

    private void runSimulation(InterventionSuite interventions) {
        long currentTime = System.currentTimeMillis();

        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        Log.setLevel(Level.INFO); // THERE IS NO DEBUG LOGGING (AND ONLY MINIMAL TRACE)!

        LOGGER.info("Using Simulation Configuration File : file://" + new File(simSpec.getFilename()).getAbsolutePath()); // use of file:// here is simply to make the link clickable in console

        simulation = new CloudSimPlus(0.01); // trying to ensure all events are processed, without any misses

        if(SimulationConfig.DURATION > 0) {
            simulation.terminateAt(SimulationConfig.DURATION);
        }

        datacenter = createDatacenter();

        // creates a broker; software acting on behalf of the cloud customer to manage their VMs & Cloudlets
        broker = new DatacenterBrokerSimple(simulation);
        // likely need to experiment here later - with different scheduling/allocation strategies
        // perhaps one that creates new cloudlets when others finish - simulating open-ended execution
        // or just make them long life - is that not more realistic of the enterprise?

        //broker.setVmDestructionDelay(100); // doesn't necessarily result in VM's hanging around to complete unfinished cloudlets!

        if (SimSpecInterfaceHomogenous.class.isAssignableFrom(simSpec.getClass())) {
            vmList = createVms();
        } else {
            vmList = createVmsFromHost();
        }

        // old/simple method (legacy approach, pre app abstraction model)
        //cloudletList = CloudletHelper.createCloudlets(simSpec, simSpec.getCloudletSpecification().getCloudlets(), vmList);

        // create a list of cloudlets (cloud applications), using the abstraction model;
        AbstractAppModel app;
        LOGGER.info("App Type : " + simSpec.getApplicationType());
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
        broker.submitCloudletList(cloudletList);

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
        });*/

        /*
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
        final BigInteger[] lastAccumulatedMips = {BigInteger.valueOf(0)};

        // FIXME - later can set these to the underlying sim variables...
        final double INTERVAL = 0.1;   // seconds (every 1/10th second - expect 36,000 readings for the normal 1 hr simulation)
        final double END_TIME = 3600;  // 1 hour

        AtomicInteger tickCount = new AtomicInteger();
        // used to do this via addOnClockTickListener, but that is NOT consistent
        // you will get a variable number of events, with random load
        // better to use a custom sampler at a fixed interval - for charting etc
        new Sampler(simulation, INTERVAL, END_TIME, t -> {
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
                BigInteger accumulatedMipsDelta = app.totalAccumulatedMipsAll.subtract(lastAccumulatedMips[0]);
                lastAccumulatedMips[0] = app.totalAccumulatedMipsAll;
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
                workDoneCumTimeSeries.add(app.totalAccumulatedMipsAll.doubleValue());
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
                double operationalCumulative = new Carbon().energyToCarbon(cumulativeEnergyDescaled); // operational emissions per sampling interval! cumulative

                double operational = new Carbon().energyToCarbon(currentEnergyPerInterval);

                double embodiedTotal = 0; // FIXME, for each per server value, use the total embodied for all servers (fudge so that I can only use 1 host for the time series)
                for (Host hostNested : datacenter.getHostList()) {
                    HostSimpleFixed hsf = (HostSimpleFixed) hostNested;
                    embodiedTotal += hsf.embodiedEmissions;
                }
                sciTimeSeries.add(Sci.calculateSci(operational, (embodiedTotal/36000), accumulatedMipsDelta.doubleValue()));
                hostSci.put(host.getId(), sciTimeSeries);

                sciCumTimeSeries.add(Sci.calculateSci(operationalCumulative, embodiedTotal, app.totalAccumulatedMipsAll.doubleValue()));
                hostSciCumulative.put(host.getId(), sciCumTimeSeries);
            }

            // generate web workloads (now insider sampler, for better control. can also log/chart easier - FIXME move to above logic, rather than std out)
            double time = simulation.clock();
            List<Cloudlet> newCloudlets = app.generateWorkloadAtTime(time, vmList);
            if (!newCloudlets.isEmpty()) {
                broker.submitCloudletList(newCloudlets);
            }
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
            long incompleteMIPS = 0;
            for(Cloudlet cloudlet : broker.getCloudletSubmittedList()) {
                if(cloudlet.isRunning()) {
                    //LOGGER.error("CLOUDLET STILL RUNNING : " + cloudlet.getId());
                    //LOGGER.error("CLOUDLET FINISHED SO FAR : " + cloudlet.getFinishedLengthSoFar());
                    incompleteMIPS += cloudlet.getFinishedLengthSoFar();
                }
            }
            LOGGER.error("MIPS associated with incomplete cloudlets (in flight) : " + incompleteMIPS);
            // FIXME in theory this should be counted towards 'work completed', but its usually small
        }

        LOGGER.info("Simulation End Time " + simulation.clockInHours() + "h or " + simulation.clockInMinutes() + "m");
        //System.err.println("tickCount (sampling) " + tickCount.get()); // maps roughly to scheduling interval

        // simulation complete - calculate work done...

        //long totalWorkExpected;
        // work expected is a function of how long we run the simulation for (not pre-determined)
        long totalWorkExpectedMax; // theoretical max, if you used all cores

        if (SimSpecInterfaceHomogenous.class.isAssignableFrom(simSpec.getClass())) { // legacy template
            // important - not theoretical max, but rather how many cores did you ask to use (in configuration)!
            //totalWorkExpected = 1L * simSpec.getHostSpecification().getHost_mips() * simSpec.getHostSpecification().getHosts() * simSpec.getCloudletSpecification().getCloudlet_pes() * SimulationConfig.DURATION; // legacy approach (homogenous)
            totalWorkExpectedMax = 1L * simSpec.getHostSpecification().getHost_mips() * simSpec.getHostSpecification().getHosts() * simSpec.getHostSpecification().getHost_pes() * SimulationConfig.DURATION;
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
        //LOGGER.info(totalWorkExpected + " = Total Work Expected (MIPS)");
        LOGGER.info(totalWorkExpectedMax + " = Total Work Expected (MIPS)"); // used to call this MAX

        // this is based on completing cloudlets incrementing a work completed counter, using the cloudlet length, from the cloudlet specification
        // disabling for now, just noise - they should all be the same!
        //LOGGER.info(app.totalAccumulatedMips + " = Total Work Completed (MIPS)");
        //LOGGER.info(app.totalAccumulatedMipsAll + " = Total Work Completed - NEW (MIPS)");

        // this is based on the actual completed cloudlet length (not expected or specified), so should be closer to the truth!
        BigInteger actualAccumulatedMips = new BigInteger(String.valueOf(0)); // TOTAL length, across ALL cores
        for (Cloudlet cloudlet : broker.getCloudletFinishedList()) {
            actualAccumulatedMips = actualAccumulatedMips.add(BigInteger.valueOf(cloudlet.getTotalLength()));
        }
        LOGGER.info(actualAccumulatedMips + " = Actual Work Completed (MIPS)");

        //calculateWorkDelta(totalWorkExpected, totalWorkExpectedMax, actualAccumulatedMips);
        calculateWorkDelta(totalWorkExpectedMax, actualAccumulatedMips);

        if(SimulationConfig.DURATION == -1) { // don't bother calculating this - usually using a fixed time frame
            //calculateTimeDelta(totalWorkExpected);
            calculateTimeDelta(totalWorkExpectedMax);
        }

        // process / output / visualise results...

        final var cloudletFinishedList = broker.getCloudletCreatedList(); // safer than getCloudletFinishedList

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
        //printCloudletExecutionStatsPerVcpu(vmList);
        //printCloudletExecutionStatsPerVcpuTabular(vmList);

        // custom utilisation logic...
        Map<Long, Double> hostUtilisation = new HashMap<>();
        for (Host host : hostList) {
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
        exportTimeSeriesData(hostEnergyRaw, null, "energy",  friendlyDate + "/sim_data_energy_" + friendlyDate + ".csv");
        exportTimeSeriesData(hostUtil, null, "utilisation", friendlyDate + "/sim_data_util_" + friendlyDate + ".csv");
        exportTimeSeriesData(hostWork, hostWorkCumulative, "work", friendlyDate + "/sim_data_work_" + friendlyDate + ".csv");
        exportTimeSeriesData(hostSci, hostSciCumulative, "sci",friendlyDate + "/sim_data_sci_" + friendlyDate + ".csv");

        Power power = new Power();
        double totalEnergy = power.calculateTotalEnergy(hostList, hostUtilisation, actualAccumulatedMips);
        energyMap.put(simCount, totalEnergy);
        workMap.put(simCount, actualAccumulatedMips.doubleValue());

        List<Double> chartResults = new ArrayList<>();
        chartResults.add(totalEnergy);
        chartResults.add(actualAccumulatedMips.doubleValue());
        chartResults.add(power.sci);
        chartMap.put(simCount, chartResults);

        simCount++;

        currentTime = System.currentTimeMillis() - currentTime;
        LOGGER.info("Simulation Elapsed Time (real world) = " + currentTime/1000 + "s");
    }

    // FIXME cumulative could just be calculated in Excel!
    public void exportTimeSeriesData(Map<Long, List> map, Map<Long, List> mapCumulative, String name, String fileName) {
        // note this is not using the above averages, but the full time series!
        StringBuilder csv = new StringBuilder();

        // header
        String SEPERATOR = ";";
        csv.append("time");
        csv.append(SEPERATOR);
        csv.append(name);
        if(mapCumulative != null) {
            csv.append(SEPERATOR);
            csv.append(name + " cumulative");
        }
        /* // now just printing the first host only, to reduce processing (they are generally the same)
        hostUtil.keySet().stream()
                .sorted()
                .forEach(hostId -> csv.append(",host").append(hostId)); */
        csv.append("\n");

        // assume all hosts have the same number of samples
        int numSteps = map.values().iterator().next().size();

        // for each timestep, print values for each host (or just one of them!)
        for (int t = 0; t < numSteps; t++) {
            csv.append(t); // or actual time if you have it
            for (Long hostId : map.keySet().stream().sorted().toList()) {
                if(hostId == 0) { // always the first host - they will all be the same, normally
                    if(name.equals("sci")) {
                        csv.append(SEPERATOR).append(String.format("%.4f", map.get(hostId).get(t)));
                        if(mapCumulative != null) {
                            csv.append(SEPERATOR).append(String.format("%.4f", mapCumulative.get(hostId).get(t)));
                        }
                    } else {
                        csv.append(SEPERATOR).append(map.get(hostId).get(t));
                        if(mapCumulative != null) {
                            csv.append(SEPERATOR).append(mapCumulative.get(hostId).get(t));
                        }
                    }
                } // don't bother logging all - the sim will generally distribute load evenly (so they will all be the same)
            }
            csv.append("\n");
        }

        Utilities.writeCsv(csv.toString(), "data/" + fileName);
    }

    /**
     * MIPS Before/After Not Equal - incomplete work (or more than expected)
     * @param totalWorkExpected
     * @param actualAccumulatedMips
     * @return
     */
    //public double calculateWorkDelta(long totalWorkExpected, long totalWorkExpectedMax, BigInteger actualAccumulatedMips) {
    public double calculateWorkDelta(long totalWorkExpected, BigInteger actualAccumulatedMips) {
        // acceptable error - 5 minute(s) of processing time (<5%)
        // long acceptableError = simSpec.getHostSpecification().getHost_mips() * simSpec.getHostSpecification().getHosts() * (5 * 60);
        // moving to pure percentage based approach (+ support for heterogeneous hardware)

        long deltaWork = BigInteger.valueOf(totalWorkExpected).subtract(actualAccumulatedMips).intValue();

        BigInteger expectedBig = BigInteger.valueOf(totalWorkExpected);
        BigInteger delta = expectedBig.subtract(actualAccumulatedMips).abs();

        //BigInteger expectedBigMax = BigInteger.valueOf(totalWorkExpectedMax);
        //BigInteger deltaMax = expectedBigMax.subtract(actualAccumulatedMips).abs();

        // convert to double for percentage calculation
        double deltaPercentage = delta.doubleValue() / totalWorkExpected * 100;
        //double deltaPercentageMax = deltaMax.doubleValue() / totalWorkExpectedMax * 100;

        if(deltaWork > 0 && deltaPercentage > ACCEPTABLE_WORKLOAD_ERROR) {
            LOGGER.warn(deltaWork + " (" + Maths.quickRound(deltaPercentage) + "%) = Unfinished MIPS");
        } else if(deltaWork < 0 && deltaPercentage > ACCEPTABLE_WORKLOAD_ERROR) {
            LOGGER.warn(Math.abs(deltaWork) + " = Excess Work (MIPS) = " + Maths.quickRound(deltaPercentage) + "%");
        }

        LOGGER.info(Maths.quickRound((100 - deltaPercentage)) + "% = Utilisation (using work complete)");
        //LOGGER.info((100 - deltaPercentageMax) + "% = Utilisation (using theoretical max)");

        return deltaPercentage;
    }

    public double calculateTimeDelta(long totalWorkExpected) {
        //double expectedCompletionTimeS = (simSpec.SIM_TOTAL_WORK / (simSpec.HOST_PES * simSpec.HOST_MIPS)); // doesn't matter how many cores the host has, we can only use 1 host at a time (with space scheduler)
        double expectedCompletionTimeS = (totalWorkExpected / (simSpec.getHostSpecification().getHosts() * simSpec.getHostSpecification().getHost_mips()));
        LOGGER.debug("Expected Completion Time " + (expectedCompletionTimeS / 60 / 60) + "hr(s)");
        LOGGER.debug("Actual Completion Time " + simulation.clockInHours() + "hr(s)");
        double timeDelta = simulation.clock() - expectedCompletionTimeS;
        if (timeDelta > 100) { // a small tolerance for error
            LOGGER.warn("Gap in expected completion time (assuming full utilisation) = " + timeDelta + "s");
        }
        return timeDelta;
    }

    /**
     * prints cloudlet execution MIPS and percentages, per VCPU
     * e.g.
     * Cloudlet 0 ran on VM 0 on vCPU 0 for 3428191.0 MIPS, which is 6.25% of total execution.
     * @param vmList
     */
    public void printCloudletExecutionStatsPerVcpu(List<CustomVm> vmList) {
        for(CustomVm vm : vmList) {
            Map<Integer, Map<Integer, Double>> vcpuMipsUsageMap = vm.getVcpuMipsConsumedMap();

            // for each vCPU
            for (Map.Entry<Integer, Map<Integer, Double>> vcpuEntry : vcpuMipsUsageMap.entrySet()) {
                int vcpuIndex = vcpuEntry.getKey();
                Map<Integer, Double> cloudletMipsMap = vcpuEntry.getValue();

                // For each cloudlet, calculate MIPS usage and percentage
                for (Map.Entry<Integer, Double> cloudletEntry : cloudletMipsMap.entrySet()) {
                    int cloudletId = cloudletEntry.getKey();
                    double mipsUsed = cloudletEntry.getValue();
                    //double totalCloudletMips = 0.0;

                    // calculate total MIPS used by cloudlet (across all vCPUs)
                    /*
                    for (Map<Integer, Double> mipsUsage : vcpuMipsUsageMap.values()) {
                        totalCloudletMips += mipsUsage.getOrDefault(cloudletId, 0.0);
                    }
                    */

                    // calculate percentage of execution time on this vCPU for this cloudlet
                    // will always simply be the percentage split across cores, regardless of completeness
                    //double percentage = (mipsUsed / totalCloudletMips) * 100;

                    double totalLength = 0;
                    // more useful - calculate percentage completeness of overall cloud execution
                    // double percentage = (mipsUsed / vm.getCloudletScheduler().getCloudletList().get(cloudletId).getTotalLength()) * 100;
                    // FIXME total length not behaving - doesn't seem to actually use getPesNumber! or rather uses it for the cloudlet, not the VM
                    // FIXME also must be a more efficient way of finding a cloudlet by ID (without this iteration and without going via the broker)
                    for (Cloudlet c : broker.getCloudletCreatedList()) {
                        if (c.getId() == cloudletId) {
                            totalLength = c.getTotalLength();
                        }
                    }
                    double percentage = (mipsUsed / totalLength) * 100;
                    // FIXME round small numbers to zero, later

                    System.out.println("Cloudlet " + cloudletId + " ran on VM " + vm.getId() + " on vCPU " + vcpuIndex + " for " + mipsUsed + " MIPS, which is " + percentage + "% of total execution.");
                }
            }
        }
    }

    /**
     * very similar to printCloudletExecutionStatsPerVcpu
     * same data, just in tabular format, e.g.
     * | VM ID      | vCPU Index | Cloudlet ID | MIPS Used    | Execution % |
     * ----------------------------------------------------------------------
     * | 0          | 0          | 0           | 3428191.00   | 6.25        |
     * @param vmList
     */
    public void printCloudletExecutionStatsPerVcpuTabular(List<CustomVm> vmList) {
        // display header
        System.out.println("----------------------------------------------------------------------");
        System.out.println(String.format("| %-10s | %-10s | %-11s | %-12s | %-11s |", "VM ID", "vCPU Index", "Cloudlet ID", "MIPS Used", "Execution %"));
        System.out.println("----------------------------------------------------------------------");

        for(CustomVm vm : vmList) {
            Map<Integer, Map<Integer, Double>> vcpuMipsUsageMap = vm.getVcpuMipsConsumedMap();

            // check - if you are not using the new custom scheduler, this won't be populated!
            if(vm.getVcpuMipsConsumedMap().size() == 0) {
                System.err.println("getVcpuMipsConsumedMap IS EMPTY!");
            }

            // iterate through the vCPU and cloudlet MIPS usage map
            for (Map.Entry<Integer, Map<Integer, Double>> vcpuEntry : vcpuMipsUsageMap.entrySet()) {
                int vcpuIndex = vcpuEntry.getKey();
                Map<Integer, Double> cloudletMipsMap = vcpuEntry.getValue();

                // for each cloudlet on this vCPU
                for (Map.Entry<Integer, Double> cloudletEntry : cloudletMipsMap.entrySet()) {
                    int cloudletId = cloudletEntry.getKey();
                    double mipsUsed = cloudletEntry.getValue();

                    // calculate total MIPS used by the cloudlet (across all vCPUs)
                    /*
                    double totalCloudletMips = 0.0;
                    for (Map<Integer, Double> mipsUsage : vcpuMipsUsageMap.values()) {
                        totalCloudletMips += mipsUsage.getOrDefault(cloudletId, 0.0);
                    }
                    */

                    // calculate percentage of execution time for this cloudlet on this vCPU
                    //double percentage = (mipsUsed / totalCloudletMips) * 100;

                    double totalLength = 0;

                    // FIXME rather wastefully looking at all cloudlets when I shouldn't need to
                    for (Cloudlet c : broker.getCloudletCreatedList()) {
                        if (c.getId() == cloudletId) {
                            //totalLength = vm.getPesNumber() * c1.getTotalLength();
                            totalLength = c.getTotalLength();
                        }
                    }
                    double percentage = (mipsUsed / totalLength) * 100;

                    // print row for this cloudlet on this vCPU
                    System.out.println(String.format("| %-10d | %-10d | %-11d | %-12.2f | %-11.2f |", vm.getId(), vcpuIndex, cloudletId, mipsUsed, percentage));
                }
            }
        }

        // end of table
        System.out.println("----------------------------------------------------------------------");
    }

    // create a Datacenter and its Hosts
    private Datacenter createDatacenter() {
        if (SimSpecInterfaceHomogenous.class.isAssignableFrom(simSpec.getClass())) {
            LOGGER.warn("Using the legacy template!"); // all hosts are equal
            hostList = new ArrayList<>(simSpec.getHostSpecification().getHosts());
            for(int i = 0; i < simSpec.getHostSpecification().getHosts(); i++) {
                final var host = createHost(simSpec.getHostSpecification().getHost_pes(), simSpec.getHostSpecification().getHost_mips(), simSpec.getHostSpecification().getHost_ram(), simSpec.getHostSpecification().getHost_bw(), simSpec.getHostSpecification().getHost_storage());
                hostList.add(host);
            }
        } else {
            hostList = new ArrayList<>(simSpec.getServerSpecifications().size());
            for (ServersSpecification server : simSpec.getServerSpecifications()) {
                // MIPS isn't specified - it needs to be calculated
                int mips = ServersSpecification.calculateMips(server.getSpeed());
                //System.out.println("SPEED : " + server.getSpeed() + " TO MIPS : " + mips);
                int ram = (int) (Double.parseDouble(server.getMemory().replaceAll("[^\\d.]", "")) * 1000);
                //System.out.println("RAM : " + server.getMemory() + " TO MB : " + ram);

                final var host = createHost(server.getCpu(), mips, ram, ServersSpecification.BANDWIDTH, ServersSpecification.STORAGE);
                hostList.add(host);
            }
        }

        // uses a VmAllocationPolicySimple by default to allocate VMs
        final var dc = new DatacenterSimpleFixed(simulation, hostList); // this is the critical bug fix!

        //VmAllocationPolicy vmAllocationPolicy = new VmAllocationPolicySimple();
        //dc.setVmAllocationPolicy(vmAllocationPolicy); // default, but just in case!

        dc.setVmAllocationPolicy(new VmAllocationPolicyRoundRobin()); // don't use best host for VM allocation, use first available

        dc.setSchedulingInterval(SimulationConfig.SCHEDULING_INTERVAL);
        //DatacenterSimple powerDatacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple(), 1.0); // example power code

        return dc;
    }

    private Host createHost(int pesCount, int mips, int ram, long bandwidth, long storage) {
        final var peList = new ArrayList<Pe>(pesCount);
        // list of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < pesCount; i++) {
            // uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(mips));
        }

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */

        // FIXME critical test - does this change utilisation stats?
        Host host = new HostSimpleFixed(ram, bandwidth, storage, peList);
        //Host host = new HostSimple(ram, bandwidth, storage, peList); // same effects as commenting out the idle fix - utilisation samples drop to 2!

        final var powerModel = new PowerModelHostSimple(Power.MAX_POWER, Power.STATIC_POWER);
        powerModel
                .setStartupPower(Power.HOST_START_UP_POWER)
                .setShutDownPower(Power.HOST_SHUT_DOWN_POWER);
        host.setPowerModel(powerModel);

        host.enableUtilizationStats(); // needed to calculate energy usage

        return host;
    }

    /**
     * creates a list of VMs (that have been specified in configuration)
     * @return
     */
    private List<Vm> createVms() {
        //LOGGER.info("createVms, using scheduler : " + simSpec.scheduler); // don't create a new wasted instance, just to tell the type!
        //LOGGER.info("createVms, creating " + simSpec.getVmSpecification().getVms() + " VMs, across " + simSpec.getHostSpecification().getHosts() + " hosts");
        LOGGER.info("createVms, creating " + simSpec.getVmSpecification().getVms() + " VMs");
        final var vmList = new ArrayList<Vm>(simSpec.getVmSpecification().getVms());
        for (int i = 0; i < simSpec.getVmSpecification().getVms(); i++) {
            //final var vm = new VmSimple(simSpec.VM_MIPS, simSpec.VM_PES);
            final var vm = new CustomVm(simSpec.getVmSpecification().getVm_mips(), simSpec.getVmSpecification().getVm_pes());

            vm.setRam(simSpec.getVmSpecification().getVm_ram()).setBw(simSpec.getVmSpecification().getVm_bw()).setSize(simSpec.getVmSpecification().getVm_storage());

            // uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            // may not always result in full cpu utilisation

            //vm.setCloudletScheduler(simSpec.scheduler); // note - this is important, the choice can determine if queuing is supported!
            // if I leave it off (default) - time scheduler, then no queueing and jobs execute as fast as they theoretically can

            // unclear why space scheduler appears to be blocking across hosts (preventing parallel execution)
            // remember the host also appears fully allocated all the time (which is wrong, or unexpected, but at least explains why cloudlets aren't being ran in parallel)
            // fix - each VM must have its own CloudletScheduler instance (otherwise parts of the system become confused and start sharing a single VM!)

            simSpec.getScheduler().enableCloudletSubmittedList(); // no idea why this is needed, but I want to use this list later!
            vm.setCloudletScheduler(simSpec.getScheduler());
            vm.getCloudletScheduler().enableCloudletSubmittedList();

            vm.getCloudletScheduler().addOnCloudletResourceAllocationFail(evt -> {
                LOGGER.info("Terminating Cloudlet : " + evt.getCloudlet().getId());
                // FIXME end up in here twice for the same cloudlet, not sure why

                // very hacky temporary workaround, but its working - stops them hogging the CPU...
                // it does also result in subsequent over-provisioned cloudlets being processed/rejected
                // (where as without this change they somehow end up being queued and succesfully processed!)
                //evt.getCloudlet().setStatus(Cloudlet.Status.SUCCESS);

                // FIXME all below options on their own, result in CPU hogging!
                //evt.getCloudlet().setStatus(Cloudlet.Status.CANCELED);
                //evt.getCloudlet().setStatus(Cloudlet.Status.FAILED);
                //evt.getCloudlet().setStatus(Cloudlet.Status.PAUSED);

                Cloudlet cl = evt.getCloudlet();
                Vm vmCl = cl.getVm();

                // better solution - remove it from the VM queue so it can't block
                vmCl.getCloudletScheduler().cloudletCancel(cl);
                cl.setStatus(Cloudlet.Status.FAILED); // or CANCELED if you prefer
            });

            //LOGGER.info("getCloudletScheduler : " + vm.getCloudletScheduler());

            // required for granular data collection (power)
            vm.enableUtilizationStats();

            //vm.setShutDownDelay(3000); // doesn't result in VM's hanging around to complete unfinished cloudlets!

            /*
            vm.addOnUpdateProcessingListener(info -> {
                LOGGER.trace("VM : UPDATE PROCESSING : VM ID " + info.getVm().getId() + " TIME : " + info.getTime());
            });

            vm.addOnHostDeallocationListener(info -> {
                LOGGER.trace("VM - HOST DEALLOCATION! : " + info);
            });
            */

            vmList.add(vm);
        }
        return vmList;
    }

    /**
     * creates a list of VMs - using the physical host data (they haven't been explicitly specified in configuration)
     * @return
     */
    private List<Vm> createVmsFromHost() {
        int vmCount = simSpec.getServerSpecifications().size();
        LOGGER.info("createVms, creating " + vmCount + " VMs");
        final var vmList = new ArrayList<Vm>(vmCount);

        for(ServersSpecification server : simSpec.getServerSpecifications()) {
            int IPC = 2;
            int mips = (int) (Double.parseDouble(server.getSpeed().replaceAll("[^\\d.]", "")) * 1000 * IPC);
            final var vm = new CustomVm(mips, server.getCpu());

            int ram = (int) (Double.parseDouble(server.getMemory().replaceAll("[^\\d.]", "")) * 1000);
            vm.setRam(ram);

            vm.setBw(ServersSpecification.BANDWIDTH / 10);
            vm.setSize(ServersSpecification.STORAGE / 10);

            vm.setCloudletScheduler(simSpec.getScheduler());

            vm.enableUtilizationStats();

            LOGGER.info("Creating new VM. MIPS = " + vm.getMips() + " CPUs = " + vm.getPesNumber());
            vmList.add(vm);
        }

        return vmList;
    }

}