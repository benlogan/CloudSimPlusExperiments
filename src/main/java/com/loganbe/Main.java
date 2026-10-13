package com.loganbe;

import ch.qos.logback.classic.Level;
import com.loganbe.application.AbstractAppModel;
import com.loganbe.application.BatchApp;
import com.loganbe.application.WebApp;
import com.loganbe.interventions.InterventionSuite;
import com.loganbe.power.Power;
import com.loganbe.templates.ServersSpecification;
import com.loganbe.templates.SimSpecFromFileLegacy;
import com.loganbe.templates.SimSpecInterfaceHomogenous;
import com.loganbe.utilities.*;
import com.sun.management.HotSpotDiagnosticMXBean;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimpleFixed;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.loganbe.SimulationConfig.*;

public class Main {

    private CloudSimPlus simulation;

    public Datacenter datacenter;
    private DatacenterBroker broker;

    public List<CustomVm> vmList;
    private List<Cloudlet> cloudletList;

    private SimSpecFromFileLegacy simSpec = new SimSpecFromFileLegacy("data/infra_templates/scenarioB.yaml");
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
        broker = new DatacenterBrokerSimpleFixed(simulation);

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
            throw new IllegalStateException("Unsupported application type: " + simSpec.getApplicationType());
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

        final BigInteger[] lastAccumulatedMips = new BigInteger[datacenter.getHostList().size()];
        Arrays.fill(lastAccumulatedMips, BigInteger.ZERO);

        // sampling - we used to do this via addOnClockTickListener, but that is NOT consistent
        // you will get a variable number of events, with random load
        // better to use a custom sampler at a fixed interval - for charting etc
        // Note: SAMPLING_INTERVAL is independent of SCHEDULING_INTERVAL
        // SCHEDULING_INTERVAL controls datacenter event processing
        // SAMPLING_INTERVAL controls metrics collection frequency

        SamplingMetrics metricsSampler = new SamplingMetrics(
                simulation,
                datacenter,
                broker,
                app,
                vmList,
                cloudletList,
                SimulationConfig.SAMPLING_INTERVAL,
                DURATION);
        metricsSampler.start();

        // release the startup list only after the sampler has taken its own copy of the initial workload.
        // this avoids pinning the cloudlets in Main for the lifetime of the simulation.
        cloudletList = null;

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

        /*
        try {
            // end of sim memory dump - useful to see what memory hasn't been properly released...
            dumpHeap("simulation-end-" + System.currentTimeMillis() + ".hprof", true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/

        LOGGER.info("Cloudlets finished : {}", app.cloudletCounter);

        LOGGER.info("Simulation End Time " + Maths.scaleAndRound(simulation.clockInHours()) + "h or " + Maths.scaleAndRound(simulation.clockInMinutes()) + "m");

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

        // Use the app-level counters so the broker does not need to retain cloudlet history.
        BigInteger actualAccumulatedMips = app.totalAccumulatedMi;
        BigInteger totalFinishedCloudlets = app.cloudletCounter;
        LOGGER.info(actualAccumulatedMips + " = Actual Work Completed (MI), for " + totalFinishedCloudlets + " finished cloudlets");
        LOGGER.info(app.totalAccumulatedMiAll + " = Total Work Done, including incomplete (MI), for " + totalFinishedCloudlets + " finished cloudlets");

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

        ExportTimeSeriesCsv.exportTimeSeriesData(metricsSampler.getHostEnergy(), null, "energy", generateFilename(friendlyDate,"/sim_data_energy"));
        ExportTimeSeriesCsv.exportTimeSeriesData(metricsSampler.getHostUtil(), null, "utilisation", generateFilename(friendlyDate, "/sim_data_util"));
        // unreliable method; removing this distracting data
        //ExportTimeSeriesCsv.exportTimeSeriesData(metricsSampler.getHostUtilAllocation(), null, "utilisation (allocation)", friendlyDate + "/sim_data_util_allocation_" + friendlyDate);
        ExportTimeSeriesCsv.exportTimeSeriesData(metricsSampler.getHostWork(), metricsSampler.getHostWorkCumulative(), "work", generateFilename(friendlyDate, "/sim_data_work"));
        ExportTimeSeriesCsv.exportTimeSeriesData(metricsSampler.getHostSci(), metricsSampler.getHostSciCumulative(), "sci", generateFilename(friendlyDate, "/sim_data_sci"));

        // FIXME should probably move earlier - we don't want to be counting file write times etc
        currentTime = System.currentTimeMillis() - currentTime;
        long simDuration = currentTime/1000;

        String metadata = "Simulation Date : " + friendlyDate + "\n"
                + "Simulation Duration(s) : " + simDuration + "\n"
                + "Simulation Sampling(s) : " + SimulationConfig.SAMPLING_INTERVAL + "\n"
                + "No. Hosts : " + vmList.size();
        FileUtilities.writeText(metadata, "data/" + generateFilename(friendlyDate,"/metadata") + ".txt");

        double totalEnergy = Power.calculateTotalEnergy(datacenter.getHostList(), metricsSampler.calculateAverageUtilisation(), actualAccumulatedMips, app.totalAccumulatedMiAll);
        energyMap.put(simCounter, totalEnergy);
        workMap.put(simCounter, actualAccumulatedMips.doubleValue());

        LOGGER.info("Simulation Elapsed Time (real world) = " + simDuration + "s");

        simCounter++;
    }

    /**
     * simply generate the filename (using date)
     * @param date
     * @param filename
     * @return
     */
    public String generateFilename(String date, String filename) {
        return date + filename;
    }

    /**
     * VERY useful to be able to call this from code, at the right time during execution
     * rather than relying on a manual well-timed dump taken from the profiler
     * @param path
     * @param live
     * @throws IOException
     */
    static void dumpHeap(String path, boolean live) throws IOException {
        var bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        bean.dumpHeap(path, live);
    }

}