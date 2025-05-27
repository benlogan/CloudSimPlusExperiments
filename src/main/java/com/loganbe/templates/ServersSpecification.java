package com.loganbe.templates;

/**
 * entirely new specification template, for ingesting hardware specs from file, where they are specified at the individual server level
 * this is fundamentally different from the legacy template model (basic), that assumed all servers were equal!
 * WIP
 */
public class ServersSpecification {

    public static int IPC = 2; // instructions per cycle (FIXME - should also come from config)

    // defaults (not heavily influencing sim for now, so ignore in config)
    public static long BANDWIDTH = 10_000;
    public static long STORAGE = 1_000_000;

    private String name;
    private String ip;
    private String location;
    private String os;
    private int cpu;
    private String speed;
    private String memory;
    private String storage;

    public static int calculateMips(String speed) {
        int mips = (int) (Double.parseDouble(speed.replaceAll("[^\\d.]", "")) * 1000 * ServersSpecification.IPC);
        return mips;
    }

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

    public int getCpu() {
        return cpu;
    }

    public void setCpu(int cpu) {
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

    public String getSpeed() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed = speed;
    }

}