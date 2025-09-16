package com.loganbe.templates;

import com.loganbe.templates.cloudlet.BatchAppSpecification;
import com.loganbe.templates.cloudlet.CloudletSpecification;
import com.loganbe.templates.cloudlet.WebAppSpecification;
import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;

/**
 * legacy spec (assumes hardware is homogenous)
 */
public interface SimSpecInterfaceHomogenous {

    CloudletScheduler getScheduler();

    HostSpecification getHostSpecification();

    VmSpecification getVmSpecification();

    CloudletSpecification getCloudletSpecification();

    WebAppSpecification getWebAppSpecification();

    BatchAppSpecification getBatchAppSpecification();

}