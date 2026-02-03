package org.cloudsimplus.hosts;

import org.cloudsimplus.provisioners.ResourceProvisioner;
import org.cloudsimplus.resources.HarddriveStorage;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.vms.HostResourceStatsNew;

import java.util.List;

/**
 * A Host class extension to fix a critical bug with CPU utilisation reporting
 * and introduce embodied emissions for the physical hardware
 * @author Ben Logan
 */
public class HostSimpleFixed extends HostSimple {

    // FIXME - move to config later
    public double embodiedEmissions = 900_000; // accepted average (g - to match SCI)

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

/*
    // disabling this results in far fewer counts of utilisation (2 vs 1000+) - why is that? but it actually improves the readings!
    // returning false breaks the official utilisation measure - host always appear busy, so its always 100% (makes sense)
    @Override
    public boolean isIdle() {
        // Prevent the host from becoming idle by always returning false - a workaround to a major utilisation reporting bug
        // FIXME need an actual fix in here - something that does return true when the machine actually is idle
        // but what if the machine is never idle - just under utilised. surely a utilisation measure based on idle is fundamentally flawed!?
        return false;
    }
*/

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