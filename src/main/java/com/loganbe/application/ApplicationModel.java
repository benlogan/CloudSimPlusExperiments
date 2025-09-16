package com.loganbe.application;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.List;

/**
 * cloudlet abstraction, to model useful properties of a typical software application/workload
 */
public interface ApplicationModel {

    /**
     * Cloudlets available at the start of the simulation.
     */
    List<Cloudlet> generateInitialWorkload(List<Vm> vmList);

    /**
     * Cloudlets generated dynamically at a given simulation time.
     * Return an empty list if no new work arrives.
     */
    List<Cloudlet> generateWorkloadAtTime(double currentTime, List<Vm> vmList);

}