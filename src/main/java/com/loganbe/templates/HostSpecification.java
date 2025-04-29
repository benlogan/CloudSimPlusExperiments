package com.loganbe.templates;

/**
 * object representing the specification of individual servers, loaded from file (legacy template)
 */
public class HostSpecification {

    private int hosts;                  // physical hosts
    private int host_pes;               // processing element (cores)
    private int host_mips;              // Million Instructions Per Second (MIPS). must also be per CORE (not for the CPU as a whole)
    private int host_ram;               // Megabytes (2GB)
    private long host_bw;               // Megabits/s (bandwidth) - network comms capacity (N/A for independent simulations?)
    private long host_storage;          // Megabytes (1000GB, or 1TB)

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