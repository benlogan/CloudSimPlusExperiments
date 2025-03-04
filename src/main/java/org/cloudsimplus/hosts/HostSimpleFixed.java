/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */
package org.cloudsimplus.hosts;

import org.cloudsimplus.core.ChangeableId;
import org.cloudsimplus.core.ResourceStatsComputer;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.provisioners.ResourceProvisioner;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.HarddriveStorage;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.schedulers.vm.VmScheduler;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.vms.HostResourceStats;
import org.cloudsimplus.vms.HostResourceStatsNew;
import org.cloudsimplus.vms.Vm;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A Host class that implements the most basic features of a Physical Machine
 * (PM) inside a {@link Datacenter}. It executes actions related to management
 * of virtual machines (e.g., creation and destruction). A host has a defined
 * policy for provisioning memory and bw, as well as an allocation policy for
 * PEs to {@link Vm Virtual Machines}. A host is associated to a Datacenter and
 * can host virtual machines.
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Toolkit 1.0
 */
public class HostSimpleFixed extends HostSimple {

    public HostSimpleFixed(List<Pe> peList) {
        super(peList);
    }

    public HostSimpleFixed(List<Pe> peList, boolean activate) {
        super(peList, activate);
    }

    public HostSimpleFixed(ResourceProvisioner ramProvisioner, ResourceProvisioner bwProvisioner, long storage, List<Pe> peList) {
        super(ramProvisioner, bwProvisioner, storage, peList);
    }

    public HostSimpleFixed(long ram, long bw, long storage, List<Pe> peList) {
        super(ram, bw, storage, peList);
    }

    @Override
    public void enableUtilizationStats() {
        this.cpuUtilizationStats = new HostResourceStatsNew(this, Host::getCpuPercentUtilization);
    }

    public HostSimpleFixed(long ram, long bw, HarddriveStorage storage, List<Pe> peList) {
        super(ram, bw, storage, peList);
    }

    public HostSimpleFixed(long ram, long bw, long storage, List<Pe> peList, boolean activate) {
        super(ram, bw, storage, peList, activate);
    }

    @Override
    public boolean isIdle() {
        // Prevent the host from becoming idle by always returning false
        return false;
    }

}
