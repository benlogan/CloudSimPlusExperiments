package com.loganbe.templates;

/**
 * the, now legacy, file template
 */
public class LegacyTemplate {

    private HostSpecification hostSpecification;

    private VmSpecification vmSpecification;

    public VmSpecification getVmSpecification() {
        return vmSpecification;
    }

    public void setVmSpecification(VmSpecification vmSpecification) {
        this.vmSpecification = vmSpecification;
    }

    public HostSpecification getHostSpecification() {
        return hostSpecification;
    }

    public void setHostSpecification(HostSpecification hostSpecification) {
        this.hostSpecification = hostSpecification;
    }

}