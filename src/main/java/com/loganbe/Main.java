package com.loganbe;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.CsvTable;
import org.cloudsimplus.builders.tables.HtmlTable;
import org.cloudsimplus.builders.tables.TableBuilderAbstract;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
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

    private static final double CLOUDLET_UTILISATION = 1; // 100% (extent to which the job will utilise the CPU)

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
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter = createDatacenter();

        // creates a broker; software acting on behalf of the cloud customer to manage their VMs & Cloudlets
        broker = new DatacenterBrokerSimple(simulation);
        // briefly experimented with a queueing broker - I think that's what I need
        // or a process that batches/schedules based on capacity,
        // or that simply creates new cloudlets when others finish
        // or just make them long life - is that not more realistic of the enterprise?

        vmList = createVms();
        cloudletList = createCloudlets();

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        // a scheduling interval is required to gather CPU utilisation statistics
        // but it's causing other problems with the execution of the sim
        //datacenter.setSchedulingInterval(1);
        LOGGER.info("getSchedulingInterval : {}s", datacenter.getSchedulingInterval());

        LOGGER.info("getMinTimeBetweenEvents : {}s", simulation.getMinTimeBetweenEvents());
        //simulation.terminateAt(100);
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
        Utilities.writeCsv(table.getCsvString(), "data/sim_data_" + new Date().getTime() + ".csv");

        //new Power().printHostsCpuUtilizationAndPowerConsumption(hostList);
    }

    public List hostList;

    // create a Datacenter and its Hosts
    private Datacenter createDatacenter() {
        //final var hostList = new ArrayList<Host>(HOSTS);
        hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();

            // new for power work...
            host.enableUtilizationStats();

            hostList.add(host);
        }

        // uses a VmAllocationPolicySimple by default to allocate VMs
        return new DatacenterSimple(simulation, hostList);
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

        return host;
    }

    // creates a list of VMs
    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            // uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final var vm = new VmSimple(VM_MIPS, VM_PES);

            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_STORAGE);

            // power work
            vm.enableUtilizationStats();

            vmList.add(vm);
        }

        return vmList;
    }

    // creates a list of Cloudlets (cloud applications)
    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);

        // utilizationModel defining the Cloudlets use X% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(CLOUDLET_UTILISATION);

        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
            cloudlet.setSizes(1024);
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }
}