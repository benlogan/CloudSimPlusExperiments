package com.loganbe;

import com.loganbe.application.ApplicationModel;
import com.loganbe.application.BatchApp;
import com.loganbe.application.WebApp;
import com.loganbe.interventions.InterventionSuite;
import com.loganbe.power.Power;
import com.loganbe.templates.ServersSpecification;
import com.loganbe.templates.SimSpecFromFile;
import com.loganbe.templates.SimSpecFromFileLegacy;
import com.loganbe.templates.SimSpecInterfaceHomogenous;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.CloudletsTableBuilderExtended;
import org.cloudsimplus.builders.tables.TableBuilderAbstract;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimpleFixed;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CustomCloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.cloudsimplus.schedulers.vm.VmScheduler;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.loganbe.SimulationConfig.ACCEPTABLE_WORKLOAD_ERROR;

public class Main {

    private CloudSimPlus simulation;
    private DatacenterBroker broker;
    public List<Host> hostList;
    public List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter;

    private BigInteger totalAccumulatedMips;

    //private SimSpecFromFileLegacy simSpec = new SimSpecFromFileLegacy("data/infra_templates/big_company.yaml");
    private SimSpecFromFileLegacy simSpec = new SimSpecFromFileLegacy("data/infra_templates/scenarioB.yaml");
    //private SimSpecFromFile simSpec = new SimSpecFromFile("data/infra_templates/example.yaml");

    private int simCount = 1;
    private Map<Integer, Double> energyMap = new HashMap();
    private Map<Integer, Double> workMap = new HashMap();

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class.getSimpleName());

    public static void main(String[] args) {
        long currentTime = System.currentTimeMillis();

        Main main = new Main();
        main.runSimulation(null);

        currentTime = System.currentTimeMillis() - currentTime;
        System.out.println("Simulation Elapsed Time (real world) = " + currentTime/1000 + "s");

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
    }

    private void runSimulation(InterventionSuite interventions) {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        Log.setLevel(ch.qos.logback.classic.Level.INFO); // THERE IS NO DEBUG LOGGING (AND ONLY MINIMAL TRACE)!

        LOGGER.info("Using Simulation Configuration File : file://" + new File(simSpec.getFilename()).getAbsolutePath()); // use of file:// here is simply to make the link clickable in console

        simulation = new CloudSimPlus(0.01); // trying to ensure all events are processed, without any misses

        // whenever a cloudlet completes, we will add to this number (must be reset for each sim run!)
        totalAccumulatedMips = BigInteger.valueOf(0);

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

        //cloudletList = createCloudlets(); // old/simple method (legacy approach, pre app abstraction model)

        // create a list of cloudlets (cloud applications), using the abstraction model;

        //ApplicationModel app = new BatchApp(100,((57_600_000 / 16)-50000));
        // length needs to be the theoretical max processing capacity (57_600_000)
        // divided by number of cores (because each will contribute a share of total)
        // and then slightly smaller to ensure they all have time to finish
        // otherwise you end up with nothing finishing and zero work done!

        ApplicationModel app = new WebApp(10000,10);
        // 1000 every 1 second results in too many cloudlets to log easily!
        // 500 every 1 second is roughly 50% capacity

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

        /*
        simulation.addOnClockTickListener(evt -> {
            for (Host host : datacenter.getHostList()) {
                double utilization = host.getCpuPercentUtilization();
                // this just confirms that all hosts are all utilised when they aren't (i.e. they are allocated!)
                System.out.printf("Time %.2f: Host %d CPU Utilization: %.2f%%\n",
                        simulation.clock(), host.getId(), utilization * 100);
            }
        });*/

        // periodically check for new workload (e.g. for web apps, additional cloudlets are added during sim execution)
        simulation.addOnClockTickListener(evt -> {
            double time = simulation.clock();
            List<Cloudlet> newCloudlets = app.generateWorkloadAtTime(time, vmList);
            if (!newCloudlets.isEmpty()) {
                broker.submitCloudletList(newCloudlets);
            }
        });

        //simulation.terminateAt(10000); // won't make any difference if you have unfinished cloudlets! (because the events have probably already been processed)

        // immediately before we start the sim, apply any interventions;
        if(interventions != null) {
            interventions.applyInterventions(this);
        }
        simulation.start();

        int incomplete = broker.getCloudletSubmittedList().size() - broker.getCloudletFinishedList().size();
        if (incomplete > 0) {
            LOGGER.error("Some Cloudlets Remain Unfinished : " + incomplete);
        }

        LOGGER.info("Simulation End Time " + simulation.clockInHours() + "h or " + simulation.clockInMinutes() + "m");

        // simulation complete - calculate work done...

        long totalWorkExpected = simSpec.getCloudletSpecification().getSim_total_work();
        if(simSpec.getCloudletSpecification().getCloudlet_length() == -1) {
            // work expected is a function of how long we run the simulation for (not pre-determined)

            if (SimSpecInterfaceHomogenous.class.isAssignableFrom(simSpec.getClass())) { // legacy template
                // important - not theoretical max (simSpec.getHostSpecification().getHost_pes()), but rather how many cores did you ask to use!
                totalWorkExpected = 1L * simSpec.getHostSpecification().getHost_mips() * simSpec.getHostSpecification().getHosts() * simSpec.getCloudletSpecification().getCloudlet_pes() * SimulationConfig.DURATION; // legacy approach (homogenous)
            } else {
                totalWorkExpected = 0;
                for (ServersSpecification server : simSpec.getServerSpecifications()) {
                    int mips = ServersSpecification.calculateMips(server.getSpeed()) * simSpec.getCloudletSpecification().getCloudlet_pes(); // how many do you choose to use, not how many cores are there (server.getCpu())
                    totalWorkExpected += mips;
                }
                totalWorkExpected = totalWorkExpected * SimulationConfig.DURATION;
            }
            // FIXME - not accounting for interventions! Not a major issue, just ignore the warning
        }
        LOGGER.info("Total Work Expected " + totalWorkExpected + " MIPS");

        // this is based on completing cloudlets incrementing a work completed counter, using the cloudlet length, from the cloudlet specification
        // broken! FIXME - because the length in cloudlet specification cfg file is zero (or -1)
        // if we move the new abstraction config from code, into the cloudlet spec, that should fix it! can be safely ignored
        LOGGER.info("Total Work Completed " + totalAccumulatedMips + " MIPS");
        //LOGGER.info("Total Work Completed " + (totalAccumulatedMips.multiply(BigInteger.valueOf(simSpec.HOSTS).multiply(BigInteger.valueOf(simSpec.HOST_PES)))) + " MIPS");

        // this is based on the actual completed cloudlet length (not expected or specified), so should be closer to the truth!
        BigInteger actualAccumulatedMips = new BigInteger(String.valueOf(0)); // TOTAL length, across ALL cores
        for (Cloudlet cloudlet : broker.getCloudletFinishedList()) {
            actualAccumulatedMips = actualAccumulatedMips.add(BigInteger.valueOf(cloudlet.getTotalLength()));
        }
        LOGGER.info("Actual Work Completed " + actualAccumulatedMips + " MIPS");

        calculateWorkDelta(totalWorkExpected, actualAccumulatedMips);

        if(SimulationConfig.DURATION == -1) { // don't bother calculating this - usually using a fixed time frame
            calculateTimeDelta(totalWorkExpected);
        }

        // process / output / visualise results...

        final var cloudletFinishedList = broker.getCloudletCreatedList(); // safer than getCloudletFinishedList

        // results table to stdout
        new CloudletsTableBuilderExtended(cloudletFinishedList).build(); // customised version with fixes/enhancements

        //new CloudletsTableBuilder(cloudletFinishedList, new HtmlTable()).build();
        //new CloudletsTableBuilder(cloudletFinishedList, new CsvTable()).build(); // replaced/extended below

        // results table to csv, for easy charting (new custom table type, to fetch csv as string)
        TableBuilderAbstract tableBuilder = new CloudletsTableBuilder(cloudletFinishedList, new ExportCsvTable());
        ExportCsvTable table = (ExportCsvTable) tableBuilder.getTable();
        tableBuilder.build();
        SimpleDateFormat formatter = new SimpleDateFormat("dd_MM_yyyy_HH:mm:ss");
        String friendlyDate = formatter.format(new Date());
        Utilities.writeCsv(table.getCsvString(), "data/sim_data_" + friendlyDate + ".csv");

        // new table showing activity in each core
        //System.out.println(broker.getVmCreatedList().get(0).getHost().getPeList().get(0).getStatus().toString());

        // after the simulation, print the cloudlet-to-vCPU mapping
        // the basic idea is to manually maintain a mapping of cloudlets to cores (because CloudSimPlus doesn't really do that)
        // then we can see exactly how work is being distributed
        // useful because the usual table doesn't tell us what is going on in each core (so this complements the cloudlet view/table)

        //List<CustomVm> vmList = broker.getVmCreatedList();

        //printCloudletExecutionStatsPerVcpu(vmList);
        //printCloudletExecutionStatsPerVcpuTabular(vmList);

        double totalEnergy = new Power().calculateHostsCpuUtilizationAndEnergyConsumption(hostList, actualAccumulatedMips);
        energyMap.put(simCount, totalEnergy);
        workMap.put(simCount, actualAccumulatedMips.doubleValue());
        //printCustomUtilisation(vmList, hostList);
        //new Power().printVmsCpuUtilizationAndPowerConsumption(vmList);

        // print out the new custom utilisation data (accurate!)
        // no longer needed - we are now using this measure in the power/energy code
        /*
        CustomCloudletScheduler scheduler = (CustomCloudletScheduler) simSpec.scheduler;
        for(Host host : datacenter.getHostList()) {
            double elapsedTime = scheduler.getHostElapsedTime(host.getId());
            double endTime = simulation.clock();
            double utilisation = elapsedTime / endTime * 100.0;
            LOGGER.info("getHostElapsedTime (Host " + host.getId() + ")" + " elapsed time(s) = " + Math.round(scheduler.getHostElapsedTime(host.getId())) + " utilisation = " + Math.round(utilisation) + "%");
        }*/
        simCount++;
    }

    /**
     * MIPS Before/After Not Equal - incomplete work (or more than expected)
     * @param totalWorkExpected
     * @param actualAccumulatedMips
     * @return
     */
    public double calculateWorkDelta(long totalWorkExpected, BigInteger actualAccumulatedMips) {
        // acceptable error - 5 minute(s) of processing time (<5%)
        // long acceptableError = simSpec.getHostSpecification().getHost_mips() * simSpec.getHostSpecification().getHosts() * (5 * 60);
        // moving to pure percentage based approach (+ support for heterogeneous hardware)

        long deltaWork = BigInteger.valueOf(totalWorkExpected).subtract(actualAccumulatedMips).intValue();

        BigInteger expectedBig = BigInteger.valueOf(totalWorkExpected);
        BigInteger delta = expectedBig.subtract(actualAccumulatedMips).abs();

        // convert to double for percentage calculation
        double deltaPercentage = delta.doubleValue() / totalWorkExpected * 100;

        if(deltaWork > 0 && deltaPercentage > ACCEPTABLE_WORKLOAD_ERROR) {
            LOGGER.warn("Unfinished MIPS = " + deltaWork + " (" + deltaPercentage + "%)");
        } else if(deltaWork < 0 && deltaPercentage > ACCEPTABLE_WORKLOAD_ERROR) {
            LOGGER.warn("Excess MIPS = " + Math.abs(deltaWork) + " (" + deltaPercentage + "%)");
        }

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
     * needs refactoring, but this demonstrates the custom utilisation method still works - the data structures are just messy
     * i.e. this requires summing up the utilisation across all hosts, for each VM - could be more efficient (FIXME)
     *
     * @param vmList
     * @param hostList
     */
    public void printCustomUtilisation(List<CustomVm> vmList, List<Host> hostList) {
        Map<Long, Double> hostElapsedTime = new HashMap<>();
        for (CustomVm vm : vmList) {
            CustomCloudletScheduler cloudletScheduler = (CustomCloudletScheduler) vm.getCloudletScheduler();

            for (Host host : hostList) {
                double elapsedTime = cloudletScheduler.getHostElapsedTime(host.getId());
                hostElapsedTime.put(host.getId(), hostElapsedTime.getOrDefault(host.getId(), 0.0) + elapsedTime);
            }
        }

        for (Host host : hostList) {
            double elapsedTime = hostElapsedTime.get(host.getId());
            double endTime = host.getDatacenter().getSimulation().clock();
            final double utilizationPercentMean = elapsedTime / endTime;
            System.out.println("Custom Utilisation - host ID : " + host.getId() + " elapsedTime : " + elapsedTime + " utilisation : " + utilizationPercentMean);
        }
    }

    // after simulation completes,

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

        Host host = new HostSimpleFixed(ram, bandwidth, storage, peList);

        final var powerModel = new PowerModelHostSimple(Power.MAX_POWER, Power.STATIC_POWER);
        powerModel
                .setStartupPower(Power.HOST_START_UP_POWER)
                .setShutDownPower(Power.HOST_SHUT_DOWN_POWER);
        host.setPowerModel(powerModel);

        host.enableUtilizationStats(); // needed to calculate energy usage

        /*
        host.addOnUpdateProcessingListener(info -> {
            LOGGER.trace("HOST : UPDATE PROCESSING : time = " + info.getTime() + " next cloudlet completion time = " + info.getNextCloudletCompletionTime());
            LOGGER.trace("HOST : getCpuPercentUtilization : " + info.getHost().getCpuPercentUtilization());
            LOGGER.trace("HOST : MEAN : " + info.getHost().getCpuUtilizationStats().getMean());
            // new Power().printHostsCpuUtilizationAndPowerConsumption(hostList); // useful to confirm expected end results
        });
        */

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
            // fix - each VM must have its own CloudletScheduler instance (otherwise parts of the system become consused and start sharing a single VM!)
            vm.setCloudletScheduler(simSpec.getScheduler());

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

    // creates a list of Cloudlets (cloud applications)
    private List<Cloudlet> createCloudlets() {
        LOGGER.info("Creating " + simSpec.getCloudletSpecification().getCloudlets() + " Cloudlets");
        final var cloudletList = new ArrayList<Cloudlet>(simSpec.getCloudletSpecification().getCloudlets());

        // utilizationModel defining the Cloudlets use X% of any resource all the time

        final var utilizationModel = new UtilizationModelDynamic(simSpec.getCloudletSpecification().getCloudlet_utilisation());
        //final var utilizationModel = new UtilizationModelFull(); // not making a difference!

        UtilizationModelDynamic utilizationModelMemory = new UtilizationModelDynamic(0.25); // 25% RAM

        for (int i = 0; i < simSpec.getCloudletSpecification().getCloudlets(); i++) {
            int cloudletPes = simSpec.getCloudletSpecification().getCloudlet_pes();
            if(cloudletPes == -1) { // use all available cores!
                cloudletPes = (int) vmList.get(i).getPesNumber();
            }

            final var cloudlet = new CloudletSimpleFixed(simSpec.getCloudletSpecification().getCloudlet_length(), cloudletPes, utilizationModel);

            // e.g. one core each but only 25% of the available memory (and bandwidth), to enable parallel execution
            cloudlet.setUtilizationModelRam(utilizationModelMemory);
            cloudlet.setUtilizationModelBw(utilizationModelMemory);

            // if a cloudlet finishes, assume it might have created capacity in the system for more processing!
            // shouldn't be necessary if you use a scheduler that supports queuing/waiting

            /*
            cloudlet.addOnStartListener(event -> {
                LOGGER.trace("CLOUDLET START : " + event.getCloudlet().getId() + " time = " + event.getTime());
                for(Host host : hostList) {
                    LOGGER.trace("CPU UTILISATION (START). HOST : " + host.getId() + " UTILISATION : " + host.getCpuPercentUtilization());
                }
            });*/

            cloudlet.addOnFinishListener(event -> {
                /*
                LOGGER.trace("CLOUDLET END : " + event.getCloudlet().getId() + " time = " + event.getTime());
                for(Host host : hostList) {
                    LOGGER.trace("CPU UTILISATION (END). HOST : " + host.getId() + " UTILISATION : " + host.getCpuPercentUtilization());
                }
                 */

                //there might be a simpler way to do this! if they are already paused, there is resume functionality!
                /*
                LOGGER.info("WAITING? " + event.getVm().getCloudletScheduler().getCloudletWaitingList().size());
                LOGGER.info("EXEC? " + event.getVm().getCloudletScheduler().getCloudletExecList().size());
                LOGGER.info("FINISHED? " + event.getVm().getCloudletScheduler().getCloudletFinishedList().size());
                */

                //totalAccumulatedMips = totalAccumulatedMips.add(BigInteger.valueOf(event.getCloudlet().getFinishedLengthSoFar()));
                totalAccumulatedMips = totalAccumulatedMips.add(BigInteger.valueOf(event.getCloudlet().getTotalLength())); // fixed, but doesn't necessarily mean this work is complete

                // for the default time scheduler, this waiting list is always empty, once the VM PEs are shared across all Cloudlets running inside a VM.
                // each Cloudlet has the opportunity to use the PEs for a given time-slice.
                // So this scheduler doesn't suit scenarios where you are overloading the hardware!
            });

            /*
            cloudlet.addOnUpdateProcessingListener(info -> {
                //LOGGER.trace("CLOUDLET UPDATE PROCESSING : " + info.getCloudlet().getId() + " time = " + info.getTime());

                /*
                Vm vm0 = cloudletList.get(0).getVm();
                //Vm vm1 = cloudletList.get(1).getVm();

                //System.out.println("getCpuPercentRequested : " + vm0.getCpuPercentRequested());
                System.out.println("getCpuPercentUtilization , " + vm0.getCpuPercentUtilization());
                //System.out.println("getTotalCpuMipsRequested : " + vm0.getTotalCpuMipsRequested());
                System.out.println("getHostCpuUtilization , " + vm0.getHostCpuUtilization());
                // above is how much of this host THIS VM IS using (relative)! NOT the actual host utilisation...
                System.out.println("getCpuPercentUtilization (host) , " + vm0.getHost().getCpuPercentUtilization());
                */

                /*
                for(Host host : hostList) {
                    LOGGER.trace("CPU UTILISATION (DURING). HOST : " + host.getId() + " UTILISATION : " + host.getCpuPercentUtilization());

                    LOGGER.trace("getTotalMipsCapacity : " + host.getTotalMipsCapacity()); // 16000 - agreed? yes, 16 cores x 1000 (both host and VM capacity)
                    LOGGER.trace("getCpuMipsUtilization : " + host.getCpuMipsUtilization());

                    // an unused host should have loads of MIPS capacity (but it has been allocated, even though it's not in active use)

                    // since CloudSim assigns a Cloudlet to a VM immediately, it marks the CPU as fully utilized, even if the Cloudlet is waiting
                    // so, this is by design. this is not appropriate for accurate power simulations!
                    // hence the need for calculating our own custom utilisation metric...
                }*//*

                for (Host host : datacenter.getHostList()) {
                    VmScheduler vms = host.getVmScheduler();
                    // this is a VmSchedulerSpaceShared - could this be where the problem is re scheduling of the VMs?

                    //CustomCloudletScheduler scheduler = (CustomCloudletScheduler) host.getVmList().get(0).getCloudletScheduler();
                    //System.err.println("HOST : " + host.getId() + " getCloudletExecList " + scheduler.getCloudletExecList().size());
                    //System.err.println("HOST : " + host.getId() + " getCloudletWaitingList " + scheduler.getCloudletWaitingList().size());

                    // so each host has one in execution, the same one
                    // and each host has 239 in waiting. so the system fully thinks it is parallel processing when its not!
                }
            });
            */

            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }
}