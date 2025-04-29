package com.loganbe.templates;

/**
 * object representing the specification of individual virtual servers, loaded from file
 */
public class VmSpecification {

    private int vms;

    private int vm_pes;
    private int vm_mips;            // this is per CORE, not per VM
    private int vm_ram;
    private int vm_bw;              // must be smaller than the host, doesn't impact execution time
    private int vm_storage;

    public int getVms() {
        return vms;
    }

    public void setVms(int vms) {
        this.vms = vms;
    }

    public int getVm_pes() {
        return vm_pes;
    }

    public void setVm_pes(int vm_pes) {
        this.vm_pes = vm_pes;
    }

    public int getVm_mips() {
        return vm_mips;
    }

    public void setVm_mips(int vm_mips) {
        this.vm_mips = vm_mips;
    }

    public int getVm_ram() {
        return vm_ram;
    }

    public void setVm_ram(int vm_ram) {
        this.vm_ram = vm_ram;
    }

    public int getVm_bw() {
        return vm_bw;
    }

    public void setVm_bw(int vm_bw) {
        this.vm_bw = vm_bw;
    }

    public int getVm_storage() {
        return vm_storage;
    }

    public void setVm_storage(int vm_storage) {
        this.vm_storage = vm_storage;
    }

}