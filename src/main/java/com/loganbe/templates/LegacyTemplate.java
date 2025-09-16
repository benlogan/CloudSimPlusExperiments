package com.loganbe.templates;

/**
 * legacy file template
 */
public class LegacyTemplate {

    private String applicationType;

    private HostSpecification hostSpecification;

    private VmSpecification vmSpecification;

    private CloudletSpecification cloudletSpecification;

    private WebAppSpecification webAppSpecification;

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

    public String getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public WebAppSpecification getWebAppSpecification() {
        return webAppSpecification;
    }

    public void setWebAppSpecification(WebAppSpecification webAppSpecification) {
        this.webAppSpecification = webAppSpecification;
    }
}