package com.loganbe.utilities;

import com.loganbe.application.AbstractAppModel;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.List;

/*
 * used during sampling to create new webapp workload...
 */
public class WorkloadGenerator {

    private final List<Cloudlet> pendingCloudlets;
    private final AbstractAppModel app;
    private final List<CustomVm> vmList;
    private final DatacenterBroker broker;

    public WorkloadGenerator(final AbstractAppModel app, final List<CustomVm> vmList, final DatacenterBroker broker, List<Cloudlet> cloudlets) {
        this.app = app;
        this.vmList = vmList;
        this.broker = broker;
        this.pendingCloudlets = cloudlets;
    }

    public void generateAndSubmitWorkload(final double time) {
        final List<Cloudlet> newCloudlets = app.generateWorkloadAtTime(time, vmList);
        if (!newCloudlets.isEmpty()) {
            pendingCloudlets.addAll(newCloudlets);
        }

        submitReadyCloudlets();
    }

    private void submitReadyCloudlets() {
        if (pendingCloudlets.isEmpty()) {
            return;
        }

        final List<Cloudlet> ready = new ArrayList<>();
        final List<Vm> createdVms = broker.getVmCreatedList();

        for (final Cloudlet cloudlet : pendingCloudlets) {
            final Vm targetVm = cloudlet.getVm();
            final boolean canSubmit = targetVm == Vm.NULL ? !createdVms.isEmpty() : createdVms.contains(targetVm);
            if (canSubmit) {
                ready.add(cloudlet);
            }
        }

        if (!ready.isEmpty()) {
            pendingCloudlets.removeAll(ready);
            broker.submitCloudletList(ready);
        }
    }

}