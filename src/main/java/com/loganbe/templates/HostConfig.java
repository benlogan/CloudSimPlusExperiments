package com.loganbe.templates;

import java.util.List;

/**
 * object representing a group of servers loaded from file
 */
public class HostConfig {

    private List<HostSpecification> servers;

    public List<HostSpecification> getServers() {
        return servers;
    }

    public void setServers(List<HostSpecification> servers) {
        this.servers = servers;
    }

}