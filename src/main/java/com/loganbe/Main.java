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
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int  HOSTS = 1; // physical hosts
    private static final int  HOST_PES = 8; // processing element (cores)
    private static final int  HOST_MIPS = 1000; // Million Instructions per Second (MIPS)
    private static final int  HOST_RAM = 2048; // Megabytes (2GB)
    private static final long HOST_BW = 10_000; // Megabits/s (bandwidth)
    private static final long HOST_STORAGE = 1_000_000; // Megabytes (1000GB, or 1TB)

    private static final int VMS = 2; // virtual hosts
    private static final int VM_PES = 4;
    private static final int VM_MIPS = 500;
    private static final int VM_RAM = 1024;
    private static final int VM_BW = 1000;
    private static final int VM_STORAGE = 10_000;

    // TODO overloading cloudlets means the sim terminates early - why?
    // need to properly understand Discrete Event Simulation basics
    // fundamentally, this isn't accurate, there needs to be a delay in submitting them

    private static final int CLOUDLETS = 8;
    private static final int CLOUDLET_PES = 1; // NOT how many cores to use, rather how many are needed!
    private static final int CLOUDLET_LENGTH = 10_000; // Million Instructions (MI)

    private static final double CLOUDLET_UTILISATION = .25; // 100% (extent to which the job will utilise the CPU - AND OTHER COMPONENTS!? i.e. RAM)
    // think about this - if you want it to use 100% of CPU, it needs to use 100% of RAM too! let's try 50%, should allow for parallel run
    // but remember, because it's using less CPU, it will take longer
    // FIXME later - could look at a model where you could use all CPU. makes no sense, this is about how how the job utilises the VM
    // if it utilises all, then you can't parallel run and you need 1 vm per job!

    // defines the time intervals to keep Hosts CPU utilisation history records
    // a scheduling interval is required to gather CPU utilisation statistics
    // but it's causing other problems with the execution of the sim
    private static final double SCHEDULING_INTERVAL = 0.1; // defaults to 0! i.e. continuous processing

    // 0 or 1 and the jobs complete
    // 0.1 they don't - must result in missing events, let's try to understand why!?
    // happens at 1 second again!! and happens regardless of how many cloudlets we are using (in parallel)
    // getting closer, but still haven't quite figured this out
    // can get it working with a bigger interval, or no interval, but not with a small one!

    // needs to be over 19 to ensure they all complete (sort of makes sense)
    // 10 (not seconds - simulation time units) appears to cause a problem
    // need to understand this properly! if I specify a time longer than the jobs, then it works (the jobs complete), but we don't collect any utilisation data!
    // if you go too short (i.e. where the interval is smaller than the cloudlet completion time), then the jobs don't complete (this is not supposed to impact the running of the jobs!)

    // why would 0 let the jobs complete, but 10 doesn't!? forget about power for now...

    // note min time between events is unchanged and defaults to 0.1s

    /**
    WARN  20.22: #Datacenter 1: Vm 1 destroyed on Host 0/#DC 1. It had a total of 3 cloudlets (running + waiting). Some events may have been missed. You can try:
    (a) decreasing CloudSim's minTimeBetweenEvents and/or Datacenter's schedulingInterval attribute;
    (b) increasing broker's Vm destruction delay for idle VMs if you set it to zero;
    (c) defining Cloudlets with smaller length (your Datacenter's scheduling interval may be smaller than the time to finish some Cloudlets).
    */

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

        //simulation = new CloudSimPlus();
        //simulation = new CloudSimPlus(0.01);
        simulation = new CloudSimPlus(0.01); // extreme test - should process all events!? makes no difference to my problem!
        // because of that logic - the clock must be greater than the last process time
        // what moves the clock on - any event?!
        // think we better validate this hypothesis before I get too carried away!
        // FIXME then later - why do we get a lot of missing events at the start - got to be something wrong there

        datacenter = createDatacenter();

        // creates a broker; software acting on behalf of the cloud customer to manage their VMs & Cloudlets
        broker = new DatacenterBrokerSimple(simulation);
        // briefly experimented with a queueing broker - I think that's what I need
        // or a process that batches/schedules based on capacity,
        // or that simply creates new cloudlets when others finish
        // or just make them long life - is that not more realistic of the enterprise?

        //broker.setVmDestructionDelay(30); // not making any difference

        vmList = createVms();
        cloudletList = createCloudlets();

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        //broker.setVmDestructionDelay(10);

        LOGGER.info("getSchedulingInterval : {}s", datacenter.getSchedulingInterval());

        //simulation
        LOGGER.info("getMinTimeBetweenEvents : {}s", simulation.getMinTimeBetweenEvents());
        // defaults to 0.1s, presumably needs to be very small if we are going with small intervals
        // otherwise events will be missed! how do you change it!?

        // Add a listener for every event processed
        simulation.addOnEventProcessingListener(event -> {
            LOGGER.trace("EVENT : " + event);

            // RELEVANT TAGS (CloudSimTag)
            // FIXME - later write a converter for friendly display
            /*
            event flow appears to be roughly in this order;
             DC_REGISTRATION_REQUEST = 2
             DC_LIST_REQUEST = 4
             VM_CREATE_ACK = 32
             CLOUDLET_CREATION = 14
             CLOUDLET_SUBMIT = 16
             VM_UPDATE_CLOUDLET_PROCESSING = 41
             */

            // WORKING HERE, LET'S LOOK AT THE PROBLEM EVENT!
            // WHY ALL OF A SUDDEN AT 1 SEC, DO WE GET TWO OF THESE EVENTS?
            // I THINK THE EVENT IS FINE, ITS JUST THAT THE EXECUTION HAS STOPPED, SO THE CHECK TO SEE IF CLOCK HAS MOVED FORWARDS FAILS

            // clock starts to equal the last process time
            // but the last process time is only updated when the cloudlet processing succeeds!
            // OK - GETTING SOMEWHERE - NEXT SIMULATION DELAY BECOMES 0, JUST BEFORE EVERYTHING GOES WRONG!
            // so the next check means clock hasn't moved on - would make sense
            // but why is it happening!?
            // it's the predicted completion time of the next finishing cloudlet
            // it's normally 0.1 - i.e. the interval, so why does it go to zero prematurely!?

            // WHY DOES A DC EVENT OF TYPE 'CLOUDLET PROCESSING' SUDDENLY BECOME UNRECOGNISABLE AND TRIGGER AN ERROR

            // time hasn't passed since last processing!? why!? validate this assumption. this would totally explain the stop
        });

        simulation.addOnClockTickListener(info -> {
            LOGGER.trace("-------------------------------------------------------");
            LOGGER.trace("CLOCK TICK (START): time = " + info.getTime());
            for (Host host : datacenter.getHostList()) {
                //LOGGER.trace("HOST : " + host.getId());
                for (Vm vm : host.getVmList()) {
                    LOGGER.trace("VM : " + vm.getId() + " : Running Cloudlets = " + vm.getCloudletScheduler().getCloudletList().size());
                    for (Cloudlet cl : vm.getCloudletScheduler().getCloudletList()) {
                        System.out.println(cl + " : " + cl.getStatus() + " : Execution Time = " + cl.getTotalExecutionTime() + " : Progress = " + cl.getFinishedLengthSoFar());
                        //System.out.println(cl.getLength()); // confirmed as 10k

                        //System.out.println(cl.getTotalLength());
                        //System.out.println(cl.getFinishTime());

                        // no cloudlets finish
                        // first two get to 494 MIPS and then block (executing ~50 every 0.1s)
                        // up to 494 at 1.1 and then by 1.2 they are stuck!
                        // others sit at 50 throughout!
                        // very weird, but does feel like I'm getting somewhere!!
                        // WORKINGHERE
                        // interval is 0.1, so makes sense that we are checking on MIPS processed

                        // does the rate make sense? 50/0.1?
                        // for the first iteration, then only the first cloudlet continues processing
                        // when I'd expect them all to run in parallel
                        // but we've got that warning about requesting ram and bandwidth that isn't available
                        // we get this warning in either scenario (even when it works), but it comes at end of execution of jobs (when it works)!
                        // should they be running in parallel!! maybe its right that the second cloudlets are blocked because of RAM/bandwidth

                        // without sim termination delay, the VMs are cleaned up 1 second in
                        // interesting - so some kind of 1 second value somewhere in the system that is prompting things to stop!?

                        // something in the dc logic - updateCloudletProcessing - seems to be changing over the course of time,
                        // meaning that the processing/updating stops
                        //simulation.clock() >= lastProcessTime + simulation.getMinTimeBetweenEvents();
                        // the sim clock is (for some reason) no longer greater than the last process time!? so it starts to skip events!?
                    }
                }
            }
            LOGGER.trace("CLOCK TICK (END)");
            LOGGER.trace("-------------------------------------------------------");
        });

        //simulation.terminateAt(25); // interesting, even if I extend the run time, the cloudlets don't process - so they are stuck!
        simulation.start();

        // 1 app (10,000 MIs) on a 1000 MIs processor
        // 1 app should take 10 seconds, but it takes 20, why is that?
        // because the processor is halved at each VM
        // note - dropping the cloudlet utilisation to 50% would have a similar effect!

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

    public List hostList;

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
        final var dc = new DatacenterSimpleFixed(simulation, hostList);

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

        Host host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);

        final var powerModel = new PowerModelHostSimple(com.loganbe.Power.MAX_POWER, com.loganbe.Power.STATIC_POWER);
        powerModel
                .setStartupPower(com.loganbe.Power.HOST_START_UP_POWER)
                .setShutDownPower(com.loganbe.Power.HOST_SHUT_DOWN_POWER);
        host.setPowerModel(powerModel);

        host.enableUtilizationStats(); // needed to calculate energy usage

        // THESE EVENTS END AFTER A SECOND TOO. SO EVERYTHING JUST GRINDS TO A HALT!
        host.addOnUpdateProcessingListener(info -> {
            LOGGER.trace("HOST : UPDATE PROCESSING : time = " + info.getTime() + " next cloudlet completion time = " + info.getNextCloudletCompletionTime());
        });

        return host;
    }

    // creates a list of VMs
    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            // uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final var vm = new VmSimple(VM_MIPS, VM_PES);

            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_STORAGE);

            // required for granular data collection (power)
            vm.enableUtilizationStats();

            //vm.setShutDownDelay(10);

            vm.addOnUpdateProcessingListener(info -> {
                LOGGER.trace("VM : UPDATE PROCESSING : VM ID " + info.getVm().getId() + " TIME : " + info.getTime());
            });

            // it's not this - happens later, but the above VM events stop!
            vm.addOnHostDeallocationListener(info -> {
                System.out.println("VM - HOST DEALLOCATION! : " + info);
            });

            vmList.add(vm);
        }
        return vmList;
    }

    // creates a list of Cloudlets (cloud applications)
    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);

        // utilizationModel defining the Cloudlets use X% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(CLOUDLET_UTILISATION);
        //UtilizationModelDynamic utilizationModel = new UtilizationModelDynamic(0.25); // 25% RAM

        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
            cloudlet.setSizes(1024);

            cloudlet.addOnStartListener(event -> {
                LOGGER.trace("CLOUDLET START : " + event.getCloudlet().getId() + " time = " + event.getTime());
                //System.out.println(event.getCloudlet());
            });

            cloudlet.addOnFinishListener(event -> {
                System.out.println("CLOUDLET END (time) " + event.getTime());
                //System.out.println(event.getCloudlet());
            });

            cloudlet.addOnUpdateProcessingListener(info -> {
                LOGGER.trace("CLOUDLET UPDATE PROCESSING : " + info.getCloudlet().getId() + " time = " + info.getTime());
                // WORKINGHERE! PROGRESS - UPDATE EVENTS STOP AFTER ~1 SECOND!
                // what is stopping the execution of the cloudlet

                // LOGS...
                //TRACE 1.01: #Datacenter 1: Unknown event 41 received.
                //INFO  1.01: Simulation: Waiting more events or the clock to reach 30.0 (the termination time set). Checking new events in 0.1 seconds (Datacenter.getSchedulingInterval())
                // so what is this unknown event!? something happens, then the sim goes to waiting for more events
            });

            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }
}