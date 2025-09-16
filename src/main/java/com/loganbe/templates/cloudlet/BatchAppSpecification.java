package com.loganbe.templates.cloudlet;

/**
 * object representing batch app configuration details (loaded from file)
 */
public class BatchAppSpecification {

    private int cloudlet_count;

    public int getCloudlet_count() { return cloudlet_count; }

    public void setCloudlet_count(int cloudlet_count) { this.cloudlet_count = cloudlet_count; }
}