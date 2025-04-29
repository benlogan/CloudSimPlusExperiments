package com.loganbe.templates;

public class CloudletSpecification {

    public static final int CLOUDLETS = 10;                 // match hosts

    public static final int CLOUDLET_PES = 16;              // match PES (e.g. from VM)

    public static final double CLOUDLET_UTILISATION = 1;    // % extent to which the job will utilise the CPU (other resources i.e. RAM are specified separately)

    // unlimited - keep the work coming!
    public static final int CLOUDLET_LENGTH = -1; // any negative number means run continuously (throughput is then determined by VM capacity)
    public static final int SIM_TOTAL_WORK = -1;

}