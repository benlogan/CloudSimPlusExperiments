package com.loganbe;

import com.loganbe.power.Power;
import com.loganbe.templates.ServersSpecification;
import com.loganbe.templates.SimSpecFromFileLegacy;
import com.loganbe.templates.SimSpecInterfaceHomogenous;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InfraCreationHelper {

    public static final Logger LOGGER = LoggerFactory.getLogger(InfraCreationHelper.class.getSimpleName());

    // create a Datacenter and its Hosts
    public static Datacenter createDatacenter(SimSpecFromFileLegacy simSpec, CloudSimPlus simulation) {
        List<Host> hostList;
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

    public static Host createHost(int pesCount, int mips, int ram, long bandwidth, long storage) {
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
     * @return List of VMs
     */
    public static List<CustomVm> createVms(SimSpecFromFileLegacy simSpec) {
        //LOGGER.info("createVms, using scheduler : " + simSpec.scheduler); // don't create a new wasted instance, just to tell the type!
        //LOGGER.info("createVms, creating " + simSpec.getVmSpecification().getVms() + " VMs, across " + simSpec.getHostSpecification().getHosts() + " hosts");
        LOGGER.info("createVms, creating " + simSpec.getVmSpecification().getVms() + " VMs");
        final var vmList = new ArrayList<CustomVm>(simSpec.getVmSpecification().getVms());
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
                // (where as without this change they somehow end up being queued and successfully processed!)
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
     * @return List of VMs
     */
    public static List<CustomVm> createVmsFromHost(SimSpecFromFileLegacy simSpec) {
        int vmCount = simSpec.getServerSpecifications().size();
        LOGGER.info("createVms, creating " + vmCount + " VMs");
        final var vmList = new ArrayList<CustomVm>(vmCount);

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

            LOGGER.info("Creating new VM. MIPS = {} CPUs = {}", vm.getMips(), vm.getPesNumber());
            vmList.add(vm);
        }

        return vmList;
    }

}