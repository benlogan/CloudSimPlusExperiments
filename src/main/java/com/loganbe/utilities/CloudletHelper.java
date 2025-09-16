package com.loganbe.utilities;

import com.loganbe.templates.SimSpecFromFileLegacy;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimpleFixed;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class CloudletHelper {

    public static final Logger LOGGER = LoggerFactory.getLogger(CloudletHelper.class.getSimpleName());

    /**
     * now redundant/legacy method for creating a list of Cloudlets (cloud applications)
     * @param simSpec
     * @param cloudletCount
     * @param vmList
     * @return
     */
    public static List<Cloudlet> createCloudlets(SimSpecFromFileLegacy simSpec, int cloudletCount,  List<Vm> vmList) {
        LOGGER.info("Creating " + cloudletCount + " Cloudlets");
        final var cloudletList = new ArrayList<Cloudlet>(cloudletCount);

        // utilizationModel defining the Cloudlets use X% of any resource all the time

        final var utilizationModel = new UtilizationModelDynamic(simSpec.getCloudletSpecification().getCloudlet_utilisation());
        //final var utilizationModel = new UtilizationModelFull(); // not making a difference!

        UtilizationModelDynamic utilizationModelMemory = new UtilizationModelDynamic(0.25); // 25% RAM

        for (int i = 0; i < cloudletCount; i++) {
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

                // this approach was working well previously
                // but is commented out because the code has been refactored and we don't have visibility on this variable anymore
                //totalAccumulatedMips = totalAccumulatedMips.add(BigInteger.valueOf(event.getCloudlet().getTotalLength())); // fixed, but doesn't necessarily mean this work is complete

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
