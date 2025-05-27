package com.loganbe.templates;

import java.util.List;

/**
 * object representing a group of servers loaded from file
 */
public class HostConfig {

    private List<ServersSpecification> servers;

    private CloudletSpecification cloudletSpecification;

    public List<ServersSpecification> getServers() {
        return servers;
    }

    public void setServers(List<ServersSpecification> servers) {
        this.servers = servers;
    }

    public CloudletSpecification getCloudletSpecification() {
        return cloudletSpecification;
    }

    public void setCloudletSpecification(CloudletSpecification cloudletSpecification) {
        this.cloudletSpecification = cloudletSpecification;
    }

}