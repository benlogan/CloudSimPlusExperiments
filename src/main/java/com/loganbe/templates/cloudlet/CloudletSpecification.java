package com.loganbe.templates.cloudlet;

/**
 * object representing cloudlet configuration details (loaded from file)
 */
public class CloudletSpecification {

    private int cloudlet_pes;               // match PES (e.g. from VM)

    private double cloudlet_utilisation;    // % extent to which the job will utilise the CPU (other resources i.e. RAM are specified separately)

    // -1 = unlimited - keep the work coming!
    private int cloudlet_length;            // any negative number means run continuously (throughput is then determined by VM capacity)
    private int sim_total_work;

    public int getCloudlet_pes() {
        return cloudlet_pes;
    }

    public void setCloudlet_pes(int cloudlet_pes) {
        this.cloudlet_pes = cloudlet_pes;
    }

    public double getCloudlet_utilisation() {
        return cloudlet_utilisation;
    }

    public void setCloudlet_utilisation(double cloudlet_utilisation) {
        this.cloudlet_utilisation = cloudlet_utilisation;
    }

    public int getCloudlet_length() {
        return cloudlet_length;
    }

    public void setCloudlet_length(int cloudlet_length) {
        this.cloudlet_length = cloudlet_length;
    }

    public int getSim_total_work() {
        return sim_total_work;
    }

    public void setSim_total_work(int sim_total_work) {
        this.sim_total_work = sim_total_work;
    }

}