package com.loganbe;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.TableBuilderAbstract;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int  HOSTS = 1;                    // physical hosts
    private static final int  HOST_PES = 8;                 // processing element (cores)
    private static final int  HOST_MIPS = 1000;             // Million Instructions Per Second (MIPS). must also be per CORE (not for the CPU as a whole)
    private static final int  HOST_RAM = 2048;              // Megabytes (2GB)
    private static final long HOST_BW = 10_000;             // Megabits/s (bandwidth) - network comms capacity (N/A for independent simulations?)
    private static final long HOST_STORAGE = 1_000_000;     // Megabytes (1000GB, or 1TB)

    private static final int VMS = 2;                       // virtual hosts
    private static final int VM_PES = 4;
    private static final int VM_MIPS = 1000;                // this is per CORE, not per VM
    private static final int VM_RAM = 1024;
    private static final int VM_BW = 1000;                  // must be smaller than the host, doesn't impact execution time
    private static final int VM_STORAGE = 10_000;           // 10GB

    private static final int CLOUDLETS = 8;
    private static final int CLOUDLET_PES = 1;              // NOT how many cores to use, rather how many are needed!
    private static final int CLOUDLET_LENGTH = 10_000;      // Million Instructions (MI)

    private static final double CLOUDLET_UTILISATION = 1;   // % extent to which the job will utilise the CPU (other resources i.e. RAM are specified separately)
    // remember - because it's using less CPU, it will obviously take longer to complete
    // if it utilises all the CPU, then you can't parallel run (you'd need 1 VM per job) - not true, this is core utilisation

    CloudletScheduler scheduler = new CloudletSchedulerSpaceShared(); // experimenting with this, which should enable greater CPU utilisiation

    // defines the time intervals to keep hosts CPU utilisation history records
    // a scheduling interval is required to gather CPU utilisation statistics
    private static final double SCHEDULING_INTERVAL = 0.1; // defaults to 0! i.e. continuous processing

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter;

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class.getSimpleName());

    public static void main(String[] args) {
        new Main();
    }

    private Main() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        Log.setLevel(ch.qos.logback.classic.Level.TRACE); // THERE IS NO DEBUG LOGGING (AND ONLY MINIMAL TRACE)!

        simulation = new CloudSimPlus(0.01); // trying to ensure all events are processed, without any misses

        datacenter = createDatacenter();

        // creates a broker; software acting on behalf of the cloud customer to manage their VMs & Cloudlets
        broker = new DatacenterBrokerSimple(simulation);
        // likely need to experiment here later - with different scheduling/allocation strategies
        // perhaps one that creates new cloudlets when others finish - simulating open-ended execution
        // or just make them long life - is that not more realistic of the enterprise?

        //broker.setVmDestructionDelay(30); // not making any difference

        vmList = createVms();
        cloudletList = createCloudlets();

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

        //simulation.terminateAt(25);
        simulation.start();

        final var cloudletFinishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
        //new CloudletsTableBuilder(cloudletFinishedList, new HtmlTable()).build();
        //new CloudletsTableBuilder(cloudletFinishedList, new CsvTable()).build(); // replaced below

        // output as csv for easy charting (new custom table type, to fetch csv as string)
        TableBuilderAbstract tableBuilder = new CloudletsTableBuilder(cloudletFinishedList, new ExportCsvTable());
        ExportCsvTable table = (ExportCsvTable) tableBuilder.getTable();
        tableBuilder.build();
        //Utilities.writeCsv(table.getCsvString(), "data/sim_data_" + new Date().getTime() + ".csv");

        new Power().printHostsCpuUtilizationAndPowerConsumption(hostList);
        //new Power().printVmsCpuUtilizationAndPowerConsumption(vmList);
    }

    public List<Host> hostList;

    // create a Datacenter and its Hosts
    private Datacenter createDatacenter() {
        //final var hostList = new ArrayList<Host>(HOSTS);
        hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();

            hostList.add(host);
        }

        // uses a VmAllocationPolicySimple by default to allocate VMs
        //final var dc = new DatacenterSimple(simulation, hostList);
        final var dc = new DatacenterSimpleFixed(simulation, hostList); // this is the critical bug fix!

        VmAllocationPolicy vmAllocationPolicy = new VmAllocationPolicySimple();
        dc.setVmAllocationPolicy(vmAllocationPolicy); // default, but just in case!

        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        //DatacenterSimple powerDatacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple(), 1.0); // example power code

        return dc;
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        // list of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            // uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(HOST_MIPS));
        }

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */

        Host host = new HostSimpleFixed(HOST_RAM, HOST_BW, HOST_STORAGE, peList);

        final var powerModel = new PowerModelHostSimple(com.loganbe.Power.MAX_POWER, com.loganbe.Power.STATIC_POWER);
        powerModel
                .setStartupPower(com.loganbe.Power.HOST_START_UP_POWER)
                .setShutDownPower(com.loganbe.Power.HOST_SHUT_DOWN_POWER);
        host.setPowerModel(powerModel);

        host.enableUtilizationStats(); // needed to calculate energy usage

        ///*
        host.addOnUpdateProcessingListener(info -> {
            LOGGER.trace("HOST : UPDATE PROCESSING : time = " + info.getTime() + " next cloudlet completion time = " + info.getNextCloudletCompletionTime());
            LOGGER.trace("HOST : getCpuPercentUtilization : " + info.getHost().getCpuPercentUtilization());
            LOGGER.trace("HOST : MEAN : " + info.getHost().getCpuUtilizationStats().getMean());
            new Power().printHostsCpuUtilizationAndPowerConsumption(hostList);
        });
        //*/

        return host;
    }

    // creates a list of VMs
    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            final var vm = new VmSimple(VM_MIPS, VM_PES);

            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_STORAGE);

            // uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            // likely won't result in full cpu utilisation
            //vm.setCloudletScheduler(scheduler); // note - if I turn this on, I see queuing!
            // if I leave it off, no queueing and jobs executing as fast as they theoretically can (but CPU not being fully utilised for some reason)

            // required for granular data collection (power)
            vm.enableUtilizationStats();

            //vm.setShutDownDelay(10);

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

    // creates a list of Cloudlets (cloud applications)
    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);

        // utilizationModel defining the Cloudlets use X% of any resource all the time

        final var utilizationModel = new UtilizationModelDynamic(CLOUDLET_UTILISATION);
        //final var utilizationModel = new UtilizationModelFull(); // not making a difference!

        UtilizationModelDynamic utilizationModelMemory = new UtilizationModelDynamic(0.25); // 25% RAM

        for (int i = 0; i < CLOUDLETS; i++) {
            // use 100% of CPU, i.e. one core each
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);

            //cloudlet.setSizes(1024);

            // one core each but only 25% of the available memory (and bandwidth), to enable parallel execution
            cloudlet.setUtilizationModelRam(utilizationModelMemory);
            cloudlet.setUtilizationModelBw(utilizationModelMemory);

            ///*
            cloudlet.addOnStartListener(event -> {
                LOGGER.trace("CLOUDLET START : " + event.getCloudlet().getId() + " time = " + event.getTime());
            });

            cloudlet.addOnFinishListener(event -> {
                LOGGER.trace("CLOUDLET END (time) " + event.getTime());
            });

            cloudlet.addOnUpdateProcessingListener(info -> {
                //LOGGER.trace("CLOUDLET UPDATE PROCESSING : " + info.getCloudlet().getId() + " time = " + info.getTime());

                Vm vm0 = cloudletList.get(0).getVm();
                Vm vm1 = cloudletList.get(1).getVm();

                //System.out.println("getCpuPercentRequested : " + vm0.getCpuPercentRequested());
                System.out.println("getCpuPercentUtilization , " + vm0.getCpuPercentUtilization());
                //System.out.println("getTotalCpuMipsRequested : " + vm0.getTotalCpuMipsRequested());
                System.out.println("getHostCpuUtilization , " + vm0.getHostCpuUtilization());
                // above is how much of this host THIS VM IS using (relative)! NOT the actual host utilisation...
                System.out.println("getCpuPercentUtilization (host) , " + vm0.getHost().getCpuPercentUtilization());
            });
            //*/

            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }
}