package org.cloudsimplus.hosts;

import org.cloudsimplus.provisioners.ResourceProvisioner;
import org.cloudsimplus.resources.HarddriveStorage;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.vms.HostResourceStatsNew;

import java.util.List;

/**
 * A Host class extension to fix a critical bug with CPU utilisation reporting
 *
 * @author Ben Logan
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
        // Prevent the host from becoming idle by always returning false - a workaround to a major utilisation reporting bug
        return false;
    }

}