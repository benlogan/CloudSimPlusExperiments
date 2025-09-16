package com.loganbe.interventions;

import com.loganbe.Main;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.cloudsimplus.vms.Vm;

/**
 * a physical server uses the same amount of energy, but gets more work done
 * some of the servers have double the processing capacity (host & VM)
 * note - this will get more work done, but it won't change the energy usage
 */
public class PowerfulServers {

    private final double SAMPLE_SIZE = 0.5;     // only change some of the servers...

    private final double PROCESSING_POWER = 2;  // doubling processing capacity...

    public PowerfulServers(Main simulation) {
        double sample = simulation.hostList.size() * SAMPLE_SIZE;

        int count = 0;
        for(Host host : simulation.hostList) {
            if(count < sample) {

                for (Pe pe : host.getPeList()) {
                    // if we don't change the VM MIPS, then we still have the same capacity (i.e. we are processing the same amount of work)
                    // we'd expect utilisation to drop by 50% (and power consumption to fall)
                    // but no more work will be done!
                    pe.setCapacity(pe.getCapacity() * PROCESSING_POWER);
                }

                // looks like VM's haven't been allocated yet (sim hasn't started), so this approach won't work;
                /*
                System.out.println("VM LIST : " + host.getVmList().size());
                for (Vm vm : host.getVmList()) {
                    CustomVm cvm = (CustomVm) vm;
                    cvm.overwriteMips(cvm.getMips() * PROCESSING_POWER);
                }
                for (Vm vm : host.getVmList()) {
                    System.out.println("VM : " + vm.getId() + " VM MIPS : " + vm.getMips());
                }*/

                count++;
            } else {
                break;
            }
        }

        count = 0;
        for(Vm vm : simulation.vmList) {
            if(count < sample) {

                CustomVm cvm = (CustomVm) vm;
                cvm.overwriteMips(cvm.getMips() * PROCESSING_POWER);

                count++;
            } else {
                break;
            }
        }
    }

}