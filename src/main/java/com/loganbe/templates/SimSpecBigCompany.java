package com.loganbe.templates;

import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;

/**
 * a fully utilised model, with sequential execution
 * large scale - first attempt at simulating a large enterprise
 * currently...
 * 1 x 16 core host, running 24 x 1 hour cloudlets, sequentially
 * so taking ~24hrs
 */
public class SimSpecBigCompany {

    public static final int HOSTS = 1;                     // physical hosts
    public static final int HOST_PES = 16;                 // processing element (cores)
    public static final int HOST_MIPS = 1000;              // Million Instructions Per Second (MIPS). must also be per CORE (not for the CPU as a whole)
    public static final int HOST_RAM = 2048;               // Megabytes (2GB)
    public static final long HOST_BW = 10_000;             // Megabits/s (bandwidth) - network comms capacity (N/A for independent simulations?)
    public static final long HOST_STORAGE = 1_000_000;     // Megabytes (1000GB, or 1TB)

    public static final int VMS = 1;                       // virtual hosts (avg 100 VM's per host)
    public static final int VM_PES = HOST_PES;
    public static final int VM_MIPS = 1000;                // this is per CORE, not per VM
    public static final int VM_RAM = 2048;
    public static final int VM_BW = 1000;                  // must be smaller than the host, doesn't impact execution time
    public static final int VM_STORAGE = 10_000;           // 10GB

    public static final int CLOUDLETS = 24*HOSTS;          // base on number of available cores, X2 - i.e. will execute 2 batches sequentially

    // WORKING HERE. WHY WHEN I GIVE THE SIM 2 HOSTS AND DOUBLE THE NUMBER OF CLOUDLETS
    // DOES IT NOT DUAL RUN, RATHER IT TAKES TWICE AS LONG AND DOESN'T UTILISE 1 HOST!

    public static final int CLOUDLET_PES = VM_PES;
    // NOT how many cores to use, rather how many are needed! (or how many it's allowed to use!?)
    // in this example, use all virtual cores at our disposal
    // I don't think you can split the cloudlet across multiple VMs - so VM_PES is the max!

    public static final double CLOUDLET_UTILISATION = 1;            // % extent to which the job will utilise the CPU (other resources i.e. RAM are specified separately)

    public static final int CLOUDLET_LENGTH = 57_600_000/HOST_PES;
    // Million Instructions (MI) - I think this might be per core! so if you have a large job that you intend to split, bear that in mind!
    // this is one job that should take ~1hr (3600s) to execute, in parallel, across 16 cores

    public CloudletScheduler scheduler = new CloudletSchedulerSpaceShared();    // will complete all work - nothing unfinished

}