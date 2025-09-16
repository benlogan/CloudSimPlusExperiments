package com.loganbe.templates;

import com.loganbe.templates.cloudlet.BatchAppSpecification;
import com.loganbe.templates.cloudlet.CloudletSpecification;
import com.loganbe.templates.cloudlet.WebAppSpecification;
import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CustomCloudletScheduler;

/**
 * a fully utilised model, with sequential execution
 * modified to enable continuous execution
 *
 * NOTE - this one is now redundant and can be replaced with the big_company.yaml template
 */
public class SimSpecBigCompanyUnlimited implements SimSpecInterfaceHomogenous {

    @Override
    public CloudletScheduler getScheduler() {
        // MUST return a new instance each time (for each VM)
        return new CustomCloudletScheduler();
    }

    @Override
    public HostSpecification getHostSpecification() {
        return new HostSpecification();
    }

    @Override
    public VmSpecification getVmSpecification() {
        return new VmSpecification();
    }

    @Override
    public CloudletSpecification getCloudletSpecification() {
        return new CloudletSpecification();
    }

    @Override
    public WebAppSpecification getWebAppSpecification() { return new WebAppSpecification(); }

    @Override
    public BatchAppSpecification getBatchAppSpecification() { return new BatchAppSpecification(); }

}