package com.loganbe;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.CsvTable;
import org.cloudsimplus.builders.tables.HtmlTable;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

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
    private static final int VM_RAM = 512;
    private static final int VM_BW = 1000;
    private static final int VM_STORAGE = 10_000;

    // TODO throwing more cloudlets at the sim doesn't work, not a great start...
    // I'm also confused about the cloudlet cores - if the app is dual core, would you not expect it to complete quicker?
    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_PES = 2; // NOT how many cores to use, rather how many are needed!
    private static final int CLOUDLET_LENGTH = 10_000; // Million Instructions (MI)

    private static final double CLOUDLET_UTILISATION = 0.5; // 50%

    // 1 app should take 10 seconds surely, but it takes 20, why is that?
    // I'm using 50% utilisation!

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new Main();
    }

    private Main() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        // creates a broker; software acting on behalf of the cloud customer to manage their VMs & Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();

        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
        //new CloudletsTableBuilder(cloudletFinishedList, new CsvTable()).build();
        //new CloudletsTableBuilder(cloudletFinishedList, new HtmlTable()).build();
    }

    // create a Datacenter and its Hosts
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        // uses a VmAllocationPolicySimple by default to allocate VMs
        Datacenter dc = new DatacenterSimple(simulation, hostList);
        //dc.setSchedulingInterval(10);
        //System.out.println("Scheduling Interval : " + dc.getSchedulingInterval());
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
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
    }

    // creates a list of VMs
    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            // uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final var vm = new VmSimple(HOST_MIPS, VM_PES);
            // TODO does this make sense - the VM has the same MIPS as the physical host?
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_STORAGE);
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