package com.loganbe.templates;

import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;

public interface SimSpecInterface {

    CloudletScheduler getScheduler();

    HostSpecification getHostSpecification();

    VmSpecification getVmSpecification();

    CloudletSpecification getCloudletSpecification();

}