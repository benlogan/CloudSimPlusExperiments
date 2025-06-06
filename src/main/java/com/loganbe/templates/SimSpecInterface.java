package com.loganbe.templates;

import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;

import java.util.List;

/*
 * enables heterogeneous (mixed) hardware
 */
public interface SimSpecInterface {

    CloudletScheduler getScheduler();

    List<ServersSpecification> getServerSpecifications();

    VmSpecification getVmSpecification();

    CloudletSpecification getCloudletSpecification();

}