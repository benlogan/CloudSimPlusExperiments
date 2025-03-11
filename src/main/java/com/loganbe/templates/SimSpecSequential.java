package com.loganbe.templates;

import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;

/**
 * an under utilised model, taking 1000 seconds (16 mins) to complete, with sequential execution
 * will result in unfinished work (because of the chosen scheduler)
 */
public class SimSpecSequential {

    public static final int HOSTS = 10;                    // physical hosts
    public static final int HOST_PES = 8;                  // processing element (cores)
    public static final int HOST_MIPS = 1000;              // Million Instructions Per Second (MIPS). must also be per CORE (not for the CPU as a whole)
    public static final int HOST_RAM = 2048;               // Megabytes (2GB)
    public static final long HOST_BW = 10_000;             // Megabits/s (bandwidth) - network comms capacity (N/A for independent simulations?)
    public static final long HOST_STORAGE = 1_000_000;     // Megabytes (1000GB, or 1TB)

    public static final int VMS = 20;                      // virtual hosts
    public static final int VM_PES = 4;
    public static final int VM_MIPS = 1000;                // this is per CORE, not per VM
    public static final int VM_RAM = 1024;
    public static final int VM_BW = 1000;                  // must be smaller than the host, doesn't impact execution time
    public static final int VM_STORAGE = 10_000;           // 10GB

    public static final int CLOUDLETS = VMS*VM_PES*2;      // base on number of available cores, X2 - i.e. will execute 2 batches sequentially

    public static final int CLOUDLET_PES = 1;              // NOT how many cores to use, rather how many are needed!
    public static final int CLOUDLET_LENGTH = 1_000_000;   // Million Instructions (MI)
    public static final double CLOUDLET_UTILISATION = 1;   // % extent to which the job will utilise the CPU (other resources i.e. RAM are specified separately)

    // experimenting with this, which should enable greater CPU utilisation
    public CloudletScheduler scheduler = new CloudletSchedulerTimeShared();

}