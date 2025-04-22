package com.loganbe.templates;

import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CustomCloudletScheduler;

/**
 * a fully utilised model, with sequential execution
 * modified to enable continuous execution
 */
public class SimSpecBigCompanyUnlimited {

    public static final int HOSTS = 10;                    // physical hosts
    public static final int HOST_PES = 16;                 // processing element (cores)
    public static final int HOST_MIPS = 1000;              // Million Instructions Per Second (MIPS). must also be per CORE (not for the CPU as a whole)
    public static final int HOST_RAM = 2048;               // Megabytes (2GB)
    public static final long HOST_BW = 10_000;             // Megabits/s (bandwidth) - network comms capacity (N/A for independent simulations?)
    public static final long HOST_STORAGE = 1_000_000;     // Megabytes (1000GB, or 1TB)

    public static final int VMS = 1 * HOSTS;

    public static final int VM_PES = HOST_PES;
    public static final int VM_MIPS = 1000;                // this is per CORE, not per VM
    public static final int VM_RAM = 2048;
    public static final int VM_BW = 1000;                  // must be smaller than the host, doesn't impact execution time
    public static final int VM_STORAGE = 10_000;           // 10GB

    public static final int CLOUDLETS = HOSTS;

    public static final int CLOUDLET_PES = VM_PES;

    public static final double CLOUDLET_UTILISATION = 1;            // % extent to which the job will utilise the CPU (other resources i.e. RAM are specified separately)

    // unlimited - keep the work coming!
    public static final int CLOUDLET_LENGTH = -1; // any negative number means run continuously (throughput is then determined by VM capacity)
    public static final int SIM_TOTAL_WORK = -1;

    public static final int DURATION = 60 * 60; // 1hr
    //public static final int DURATION = 60 * 60 * 24; // 24hrs

    public CloudletScheduler getScheduler() {
        // MUST return a new instance each time (for each VM)
        return new CustomCloudletScheduler();
    }

}