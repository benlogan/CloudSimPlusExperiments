package com.loganbe.templates.cloudlet;

/**
 * object representing batch app configuration details (loaded from file)
 */
public class BatchAppSpecification {

    private int cloudlet_length;

    private int cloudlet_count;

    public int getCloudlet_length() {
        return cloudlet_length;
    }

    public void setCloudlet_length(int cloudlet_length) {
        this.cloudlet_length = cloudlet_length;
    }

    public int getCloudlet_count() { return cloudlet_count; }

    public void setCloudlet_count(int cloudlet_count) { this.cloudlet_count = cloudlet_count; }
}