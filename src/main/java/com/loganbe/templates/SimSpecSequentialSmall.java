package com.loganbe.templates;

import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.cloudlet.CustomCloudletScheduler;

/**
 * an under utilised model, with sequential execution
 * small version - used to help debug issues with unfinished cloudlets
 * 8 out of 16 will process
 * the other 8 will need to wait (but sim will end early)
 * unless you use a space scheduler (because time scheduler doesn't queue)
 */
public class SimSpecSequentialSmall {

    public static final int HOSTS = 1;                     // physical hosts
    public static final int HOST_PES = 8;                  // processing element (cores)
    public static final int HOST_MIPS = 1000;              // Million Instructions Per Second (MIPS). must also be per CORE (not for the CPU as a whole)
    public static final int HOST_RAM = 2048;               // Megabytes (2GB)
    public static final long HOST_BW = 10_000;             // Megabits/s (bandwidth) - network comms capacity (N/A for independent simulations?)
    public static final long HOST_STORAGE = 1_000_000;     // Megabytes (1000GB, or 1TB)

    public static final int VMS = 2;                       // virtual hosts
    public static final int VM_PES = 4;
    public static final int VM_MIPS = 1000;                // this is per CORE, not per VM
    public static final int VM_RAM = 1024;
    public static final int VM_BW = 1000;                  // must be smaller than the host, doesn't impact execution time
    public static final int VM_STORAGE = 10_000;           // 10GB

    public static final int CLOUDLETS = VMS*VM_PES*2;      // base on number of available cores, X2 - i.e. will execute 2 batches sequentially

    public static final int CLOUDLET_PES = 1;              // NOT how many cores to use, rather how many are needed!
    public static final int CLOUDLET_LENGTH = 1_000_000;   // Million Instructions (MI)
    public static final double CLOUDLET_UTILISATION = 1;   // % extent to which the job will utilise the CPU (other resources i.e. RAM are specified separately)

    //public static final int SIM_TOTAL_WORK = CLOUDLETS * CLOUDLET_LENGTH * CLOUDLET_PES;    // total amount of work (varies depending on how many cores you deploy cloudlets to!) - the same cloudlet can be deployed to multiple cores
    public static final int SIM_TOTAL_WORK = CLOUDLETS * CLOUDLET_LENGTH * VM_PES;

    //public CloudletScheduler scheduler = new CloudletSchedulerTimeShared();         // DON'T USE, DOESN'T SUPPORT WAITING CLOUDLETS!
    public CloudletScheduler scheduler = new CustomCloudletScheduler();
    //public CloudletScheduler scheduler = new CloudletSchedulerSpaceShared();      // will complete all work - nothing unfinished

}