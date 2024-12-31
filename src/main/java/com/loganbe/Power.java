package com.loganbe;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.HostResourceStats;

import java.util.List;

public class Power {

    // defines the power a Host uses, even if it's idle (in Watts)
    public static final double STATIC_POWER = 35;

    // max power a Host uses (in Watts)
    public static final int MAX_POWER = 50;

    // Host power consumption (in Watts) during startup
    public static final double HOST_START_UP_POWER = 5;

    // Host power consumption (in Watts) during shutdown
    public static final double HOST_SHUT_DOWN_POWER = 3;

    /**
     * The Host CPU Utilization History is only computed
     * if VMs utilization history is enabled by calling
     * {@code vm.getUtilizationHistory().enable()}.
     */
    public static void printHostsCpuUtilizationAndPowerConsumption(List<Host> hostList) {
        System.out.println();
        for (final Host host : hostList) {
            printHostCpuUtilizationAndPowerConsumption(host);
        }
        System.out.println();
        printTotalPower(hostList);
    }

    public static void printHostCpuUtilizationAndPowerConsumption(final Host host) {
        final HostResourceStats cpuStats = host.getCpuUtilizationStats();

        // the total Host's CPU utilization for the time specified by the map key
        final double utilizationPercentMean = cpuStats.getMean();
        final double watts = host.getPowerModel().getPower(utilizationPercentMean);
        System.out.printf(
                "Host %2d CPU Usage mean: %6.1f%% | Power Consumption mean: %8.0f W%n",
                host.getId(), utilizationPercentMean * 100, watts);
    }

    public static void printTotalPower(List<Host> hostList) {
        double totalPower = 0;
        for (final Host host : hostList) {
            final HostResourceStats cpuStats = host.getCpuUtilizationStats();
            final double utilizationPercentMean = cpuStats.getMean();
            final double watts = host.getPowerModel().getPower(utilizationPercentMean);
            totalPower += watts;
        }
        System.out.println("TOTAL POWER = " + totalPower + "W");
    }

}
