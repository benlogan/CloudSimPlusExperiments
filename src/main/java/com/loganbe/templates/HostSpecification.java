package com.loganbe.templates;

/**
 * object representing the specification of individual servers, loaded from file
 */
public class HostSpecification {

    private int hosts;// = 10;                    // physical hosts
    private int host_pes;// = 16;                 // processing element (cores)
    private int host_mips;// = 1000;              // Million Instructions Per Second (MIPS). must also be per CORE (not for the CPU as a whole)
    private int host_ram;// = 2048;               // Megabytes (2GB)
    private long host_bw;// = 10_000;             // Megabits/s (bandwidth) - network comms capacity (N/A for independent simulations?)
    private long host_storage;// = 1_000_000;     // Megabytes (1000GB, or 1TB)

    // new, basic approach (incomplete FIXME - currently a hybrid of the legacy template and the proposed new template)

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

    public int getHosts() {
        return hosts;
    }

    public void setHosts(int hosts) {
        this.hosts = hosts;
    }

    public int getHost_pes() {
        return host_pes;
    }

    public void setHost_pes(int host_pes) {
        this.host_pes = host_pes;
    }

    public int getHost_mips() {
        return host_mips;
    }

    public void setHost_mips(int host_mips) {
        this.host_mips = host_mips;
    }

    public int getHost_ram() {
        return host_ram;
    }

    public void setHost_ram(int host_ram) {
        this.host_ram = host_ram;
    }

    public long getHost_bw() {
        return host_bw;
    }

    public void setHost_bw(long host_bw) {
        this.host_bw = host_bw;
    }

    public long getHost_storage() {
        return host_storage;
    }

    public void setHost_storage(long host_storage) {
        this.host_storage = host_storage;
    }

}