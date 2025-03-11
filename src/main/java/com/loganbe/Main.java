package com.loganbe;

import com.loganbe.templates.SimSpecBigCompany;
import com.loganbe.templates.SimSpecSequentialSmall;
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
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Main {

    // defines the time intervals to keep hosts CPU utilisation history records
    // a scheduling interval is required to gather CPU utilisation statistics
    private static final double SCHEDULING_INTERVAL = 0.1; // defaults to 0! i.e. continuous processing

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter;

    private SimSpecBigCompany simSpec = new SimSpecBigCompany();

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class.getSimpleName());

    public static void main(String[] args) {
        new Main();
    }

    private Main() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        Log.setLevel(ch.qos.logback.classic.Level.INFO); // THERE IS NO DEBUG LOGGING (AND ONLY MINIMAL TRACE)!

        simulation = new CloudSimPlus(0.01); // trying to ensure all events are processed, without any misses

        datacenter = createDatacenter();

        // creates a broker; software acting on behalf of the cloud customer to manage their VMs & Cloudlets
        broker = new DatacenterBrokerSimple(simulation);
        // likely need to experiment here later - with different scheduling/allocation strategies
        // perhaps one that creates new cloudlets when others finish - simulating open-ended execution
        // or just make them long life - is that not more realistic of the enterprise?

        //broker.setVmDestructionDelay(100); // doesn't necessarily result in VM's hanging around to complete unfinished cloudlets!

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

        //simulation.terminateAt(10000); // won't make any difference if you have unfinished cloudlets! (because the events have probably already been processed)

        simulation.start();

        int incomplete = broker.getCloudletSubmittedList().size() - broker.getCloudletFinishedList().size();
        if (incomplete > 0) {
            LOGGER.error("Some Cloudlets Remain Unfinished : " + incomplete);
        }

        LOGGER.info("Simulation End Time " + Math.round(simulation.clockInHours()) + "h");

        BigInteger totalSubmittedMips = BigInteger.valueOf(simSpec.CLOUDLETS)
                .multiply(BigInteger.valueOf(simSpec.CLOUDLET_TOTAL_WORK));
        LOGGER.info("Total Work Submitted " + totalSubmittedMips + " MIPS");
        LOGGER.info("Total Work Completed (per core) " + totalAccumulatedMips + " MIPS");
        LOGGER.info("Total Work Completed " + (totalAccumulatedMips.multiply(BigInteger.valueOf(simSpec.HOSTS).multiply(BigInteger.valueOf(simSpec.HOST_PES)))) + " MIPS");

        final var cloudletFinishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
        //new CloudletsTableBuilder(cloudletFinishedList, new HtmlTable()).build();
        //new CloudletsTableBuilder(cloudletFinishedList, new CsvTable()).build(); // replaced below

        // output as csv for easy charting (new custom table type, to fetch csv as string)
        TableBuilderAbstract tableBuilder = new CloudletsTableBuilder(cloudletFinishedList, new ExportCsvTable());
        ExportCsvTable table = (ExportCsvTable) tableBuilder.getTable();
        tableBuilder.build();
        //Utilities.writeCsv(table.getCsvString(), "data/sim_data_" + new Date().getTime() + ".csv");

        // FIXME WORKINGHERE
        // these tables are very confusing and fundamentally wrong!
        // I think this is what has been causing so much confusion
        // the current version implies that cloudlet 0 runs exclusively at first, but only on host 0 and vm 0
        // then we start on cloudlet 1 on host 1, on vm 1
        // none of this is right. surely cloudlet 0 is actually being broken up and running on all cores simultaneously
        // I think we need a host/vm/cores table that shows that view
        // and the cloudlet view probably needs fixing to show multiple entries per row where appropriate

        new Power().printHostsCpuUtilizationAndPowerConsumption(hostList);
        //new Power().printVmsCpuUtilizationAndPowerConsumption(vmList);
    }

    public List<Host> hostList;

    // create a Datacenter and its Hosts
    private Datacenter createDatacenter() {
        hostList = new ArrayList<>(simSpec.HOSTS);
        for(int i = 0; i < simSpec.HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        // uses a VmAllocationPolicySimple by default to allocate VMs
        final var dc = new DatacenterSimpleFixed(simulation, hostList); // this is the critical bug fix!

        VmAllocationPolicy vmAllocationPolicy = new VmAllocationPolicySimple();
        dc.setVmAllocationPolicy(vmAllocationPolicy); // default, but just in case!

        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        //DatacenterSimple powerDatacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple(), 1.0); // example power code

        return dc;
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(simSpec.HOST_PES);
        // list of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < simSpec.HOST_PES; i++) {
            // uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(simSpec.HOST_MIPS));
        }

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */

        Host host = new HostSimpleFixed(simSpec.HOST_RAM, simSpec.HOST_BW, simSpec.HOST_STORAGE, peList);

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

    // creates a list of VMs
    private List<Vm> createVms() {
        LOGGER.info("createVms, using scheduler : " + simSpec.scheduler);
        LOGGER.info("createVms, creating " + simSpec.VMS + " VMs, across " + simSpec.HOSTS + " hosts");
        final var vmList = new ArrayList<Vm>(simSpec.VMS);
        for (int i = 0; i < simSpec.VMS; i++) {
            final var vm = new VmSimple(simSpec.VM_MIPS, simSpec.VM_PES);

            vm.setRam(simSpec.VM_RAM).setBw(simSpec.VM_BW).setSize(simSpec.VM_STORAGE);

            // uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            // may not always result in full cpu utilisation
            vm.setCloudletScheduler(simSpec.scheduler); // note - this is important, the choice can determine if queuing is supported!
            // if I leave it off (default) - time scheduler, then no queueing and jobs execute as fast as they theoretically can

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

    public BigInteger totalAccumulatedMips = BigInteger.valueOf(0);

    // creates a list of Cloudlets (cloud applications)
    private List<Cloudlet> createCloudlets() {
        LOGGER.info("Creating " + simSpec.CLOUDLETS + " Cloudlets");
        final var cloudletList = new ArrayList<Cloudlet>(simSpec.CLOUDLETS);

        // utilizationModel defining the Cloudlets use X% of any resource all the time

        final var utilizationModel = new UtilizationModelDynamic(simSpec.CLOUDLET_UTILISATION);
        //final var utilizationModel = new UtilizationModelFull(); // not making a difference!

        UtilizationModelDynamic utilizationModelMemory = new UtilizationModelDynamic(0.25); // 25% RAM

        for (int i = 0; i < simSpec.CLOUDLETS; i++) {
            // use 100% of CPU, i.e. one core each
            final var cloudlet = new CloudletSimple(simSpec.CLOUDLET_LENGTH, simSpec.CLOUDLET_PES, utilizationModel);

            // one core each but only 25% of the available memory (and bandwidth), to enable parallel execution
            cloudlet.setUtilizationModelRam(utilizationModelMemory);
            cloudlet.setUtilizationModelBw(utilizationModelMemory);

            // if a cloudlet finishes, assume it might have created capacity in the system for more processing!
            // shouldn't be necessary if you use a scheduler that supports queuing/waiting

            cloudlet.addOnFinishListener(event -> {
                //LOGGER.info("CLOUDLET END (time) " + event.getTime());

                //there might be a simpler way to do this! if they are already paused, there is resume functionality!
                /*
                LOGGER.info("WAITING? " + event.getVm().getCloudletScheduler().getCloudletWaitingList().size());
                LOGGER.info("EXEC? " + event.getVm().getCloudletScheduler().getCloudletExecList().size());
                LOGGER.info("FINISHED? " + event.getVm().getCloudletScheduler().getCloudletFinishedList().size());
                */

                totalAccumulatedMips = totalAccumulatedMips.add(BigInteger.valueOf(event.getCloudlet().getFinishedLengthSoFar()));

                // for the default time scheduler, this waiting list is always empty, once the VM PEs are shared across all Cloudlets running inside a VM.
                // each Cloudlet has the opportunity to use the PEs for a given time-slice.
                // So this scheduler doesn't suit scenarios where you are overloading the hardware!
            });

            /*
            cloudlet.addOnStartListener(event -> {
                LOGGER.trace("CLOUDLET START : " + event.getCloudlet().getId() + " time = " + event.getTime());
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
            */

            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }
}