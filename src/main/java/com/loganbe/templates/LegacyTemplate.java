package com.loganbe.templates;

/**
 * the, now legacy, file template
 */
public class LegacyTemplate {

    private HostSpecification hostSpecification;

    private VmSpecification vmSpecification;

    private CloudletSpecification cloudletSpecification;

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

    public CloudletSpecification getCloudletSpecification() {
        return cloudletSpecification;
    }

    public void setCloudletSpecification(CloudletSpecification cloudletSpecification) {
        this.cloudletSpecification = cloudletSpecification;
    }

}