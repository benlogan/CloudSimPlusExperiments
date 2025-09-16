package com.loganbe.templates;

import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;

import java.util.List;

/**
 * legacy spec (assumes hardware is homogenous)
 */
public interface SimSpecInterfaceHomogenous {

    CloudletScheduler getScheduler();

    HostSpecification getHostSpecification();

    VmSpecification getVmSpecification();

    CloudletSpecification getCloudletSpecification();

    WebAppSpecification getWebAppSpecification();

}