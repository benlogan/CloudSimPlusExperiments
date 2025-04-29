package com.loganbe.templates;

public class VmSpecification {

    // FIXME needs to follow the pattern from HostSpecification and load this from file

    public static final int VMS = 1 * 10;//HOSTS;

    public static final int VM_PES = 16;//HOST_PES;
    public static final int VM_MIPS = 1000;                // this is per CORE, not per VM
    public static final int VM_RAM = 2048;
    public static final int VM_BW = 1000;                  // must be smaller than the host, doesn't impact execution time
    public static final int VM_STORAGE = 10_000;           // 10GB

}