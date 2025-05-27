package org.cloudsimplus.hosts;

import org.cloudsimplus.provisioners.ResourceProvisioner;
import org.cloudsimplus.resources.HarddriveStorage;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.Resource;
import org.cloudsimplus.vms.HostResourceStatsNew;
import org.cloudsimplus.vms.Vm;

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

    /**
     * this code is only needed when investigating VM allocation problems
    @Override
    public HostSuitability getSuitabilityFor(final Vm vm) {
        final var suitability = new HostSuitability(this, vm);

        suitability.setForStorage(disk.isAmountAvailable(vm.getStorage()));
        if (!suitability.forStorage()) {
            logAllocationError(true, vm, false, "MB", this.getStorage(), vm.getStorage());
            if (lazySuitabilityEvaluation)
                return suitability;
        }

        suitability.setForRam(super.getRamProvisioner().isSuitableForVm(vm, vm.getRam()));
        if (!suitability.forRam()) {
            logAllocationError(true, vm, false, "MB", this.getRam(), vm.getRam());
            //System.err.println("PROBLEM!!!");
            if (lazySuitabilityEvaluation)
                return suitability;
        }

        suitability.setForBw(super.getBwProvisioner().isSuitableForVm(vm, vm.getBw()));
        if (!suitability.forBw()) {
            logAllocationError(true, vm, false, "Mbps", this.getBw(), vm.getBw());
            if (lazySuitabilityEvaluation)
                return suitability;
        }

        suitability.setForPes(super.getVmScheduler().isSuitableForVm(vm));
        return suitability;
    }

    private void logAllocationError(
            final boolean showFailureLog, final Vm vm,
            final boolean inMigration, final String resourceUnit,
            final Resource pmResource, final Resource vmRequestedResource) {
        if (!showFailureLog) {
            return;
        }

        final var migration = inMigration ? "VM Migration" : "VM Creation";
        final var msg = pmResource.getAvailableResource() > 0 ?
                "just " + pmResource.getAvailableResource() + " " + resourceUnit :
                "no amount";
        LOGGER.error(
                "{}: {}: [{}] Allocation of {} to {} failed due to lack of {}. Required {} but there is {} available.",
                "", getClass().getSimpleName(), migration, vm, this,
                pmResource.getClass().getSimpleName(), vmRequestedResource.getCapacity(), msg);
    }
    */

}