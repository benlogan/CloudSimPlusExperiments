package com.loganbe.templates;

/**
 * entirely new specification template, for ingesting hardware specs from file, where they are specified at the individual server level
 * this is fundamentally different from the legacy template model (basic), that assumed all servers were equal!
 * WIP
 */
public class ServersSpecification {

    private String name;
    private String ip;
    private String location;
    private String os;
    private String cpu;
    private String memory;
    private String storage;

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getCpu() {
        return cpu;
    }

    public void setCpu(String cpu) {
        this.cpu = cpu;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setName(String name) {
        this.name = name;
    }

}