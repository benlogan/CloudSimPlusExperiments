package com.loganbe.power;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.power.models.PowerModel;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.schedulers.cloudlet.CustomVm;
import org.cloudsimplus.vms.HostResourceStats;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmResourceStats;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.List;

import static java.util.Comparator.comparingLong;

public class Power {

    // defines the power a Host uses, even if it's idle (in Watts)
    public static final double STATIC_POWER = 520;
    // Idle power ~60–70% of max is typical for servers (we'll use 65% of 800W = 520W)

    // max power a Host uses (in Watts)
    public static final int MAX_POWER = 800;

    // Host power consumption (in Watts) during startup
    public static final double HOST_START_UP_POWER = 5;

    // Host power consumption (in Watts) during shutdown
    public static final double HOST_SHUT_DOWN_POWER = 3;

    /**
     * Prints the following information from VM's utilization stats:
     * <ul>
     *   <li>VM's mean CPU utilization relative to the total Host's CPU utilization.
     *       For instance, if the CPU utilization mean of two equal VMs is 100% of their CPU, the utilization
     *       of each one corresponds to 50% of the Host's CPU utilization.</li>
     *   <li>VM's power consumption relative to the total Host's power consumption.</li>
     * </ul>
     *
     * <p>A Host, even if idle, may consume a static amount of power.
     * Lets say it consumes 20 W in idle state and that for each 1% of CPU use it consumes 1 W more.
     * For the 2 VMs of the example above, each one using 50% of CPU will consume 50 W.
     * That is 100 W for the 2 VMs, plus the 20 W that is static.
     * Therefore we have a total Host power consumption of 120 W.
     * </p>
     *
     * <p>
     * If we compute the power consumption for a single VM by
     * calling {@code vm.getHost().getPowerModel().getPower(hostCpuUsage)},
     * we get the 50 W consumed by the VM, plus the 20 W of static power.
     * This adds up to 70 W. If the two VMs are equal and using the same amount of CPU,
     * their power consumption would be the half of the total Host's power consumption.
     * This would be 60 W, not 70.
     * </p>
     *
     * <p>This way, we have to compute VM power consumption by sharing a supposed Host static power
     * consumption with each VM, as it's being shown here.
     * Not all {@link PowerModel} have this static power consumption.
     * However, the way the VM power consumption
     * is computed here, that detail is abstracted.
     * </p>
     */
    public static void printVmsCpuUtilizationAndPowerConsumption(List<CustomVm> vmList) {
        //System.out.println();
        System.out.println("\nVMs - CPU Utilisation Stats");
        vmList.sort(comparingLong(vm -> vm.getHost().getId()));
        for (Vm vm : vmList) {
            final var powerModel = vm.getHost().getPowerModel();
            final double hostStaticPower = powerModel instanceof PowerModelHostSimple powerModelHost ? powerModelHost.getStaticPower() : 0;
            final double hostStaticPowerByVm = hostStaticPower / vm.getHost().getVmCreatedList().size();

            //VM CPU utilization relative to the host capacity
            final double vmRelativeCpuUtilization = vm.getCpuUtilizationStats().getMean() / vm.getHost().getVmCreatedList().size();
            final double vmPower = powerModel.getPower(vmRelativeCpuUtilization) - hostStaticPower + hostStaticPowerByVm; // W
            final VmResourceStats cpuStats = vm.getCpuUtilizationStats();
            System.out.printf(
                    "Vm   %2d CPU Usage Mean: %6.1f%% | Power Consumption Mean: %8.0f W%n",
                    vm.getId(), cpuStats.getMean() *100, vmPower);
        }
        System.out.println();
    }

    /**
     * The Host CPU Utilization History is only computed
     * if VMs utilization history is enabled by calling
     * {@code vm.getUtilizationHistory().enable()}.
     */
    public static double calculateHostsCpuUtilizationAndEnergyConsumption(List<Host> hostList, BigInteger workDone) {
        /*
        // quite noisy logging!
        System.out.println("\nPhysical Host - CPU Utilisation Stats");
        for (Host host : hostList) {
            printHostCpuUtilizationAndPowerConsumption(host);
        }
        */

        /*
        System.out.println("\nPhysical Host - CPU Utilisation Stats (accurate)");
        for (Host host : hostList) {
            printHostCpuUtilizationAndPowerConsumptionFixed(host);
        }*/

        System.out.println();
        return calculateTotalEnergy(hostList, workDone);
    }

    /**
     * WARNING this is not always showing physical host to be 100% utilised when space sharing
     * this may not be a reliable measure of utilisation
     * this is not suitable for use in energy calculations
     * it can be used, but it is based more on allocation
     * which can, in some situations, differ from actual usage
     *
     * @param host
     */
    public static void printHostCpuUtilizationAndPowerConsumption(Host host) {
        // framework/default method - for some unknown reason, no longer reliable when using the new mixed hardware template!
        HostResourceStats cpuStats = host.getCpuUtilizationStats();
        final double utilizationPercentMean = cpuStats.getMean();
        final double watts = host.getPowerModel().getPower(utilizationPercentMean);

        System.out.printf(
                "Host %2d CPU Usage mean: %6.1f%% | Power Consumption mean: %8.0fW%n",
                host.getId(), utilizationPercentMean * 100, watts);
    }

    public static void printHostCpuUtilizationAndPowerConsumptionFixed(Host host) {
        // custom method
        PowerServer ps = wattsPerServer(host);
        final double utilizationPercentMean = ps.getUtilizationPercentMean();
        final double watts = ps.getWatts();

        System.out.printf(
                "Host %2d CPU Usage mean: %6.1f%% | Power Consumption mean: %8.0fW%n",
                host.getId(), utilizationPercentMean * 100, watts);
    }

    public static double calculateTotalEnergy(List<Host> hostList, BigInteger workDone) {
        double totalPower = 0;
        double totalEnergy = 0;
        double upTimeHours = 0;
        double embodiedTotal = 0;
        for (final Host host : hostList) {
            // framework method (OLD)
            final HostResourceStats cpuStats = host.getCpuUtilizationStats();
            final double utilizationPercentMean = cpuStats.getMean();
            final double watts = host.getPowerModel().getPower(utilizationPercentMean);

            totalPower += watts;

            upTimeHours = host.getTotalUpTime() / 60 / 60;
            double energy = watts * upTimeHours;
            totalEnergy += energy;

            // new method (custom utilisation) - no longer required?
            /*
            PowerServer ps = wattsPerServer(scheduler, host);

            totalPower += ps.getWatts();

            upTimeHours = host.getTotalUpTime() / 60 / 60;
            double energy = ps.getWatts() * upTimeHours;
            totalEnergy += energy;
            */

            HostSimpleFixed hsf = (HostSimpleFixed) host;
            embodiedTotal += hsf.embodiedEmissions;
        }
        DecimalFormat df = new DecimalFormat("#");

        System.out.println("Total Power = " + df.format(totalPower) + "W");

        System.out.println("Energy Consumption = " + df.format(totalPower) + "Wh (i.e. per hour)");

        DecimalFormat df1 = new DecimalFormat("#.##");
        System.out.println("Total Compute Energy = " + df.format(totalEnergy) + "Wh (" + df1.format(totalEnergy/1000) + "kWh) consumed in " + df1.format(upTimeHours) + "hr(s)");

        // energy efficiency - work done (MIPS), per unit of energy required/consumed
        BigDecimal bd = BigDecimal.valueOf(totalEnergy / workDone.doubleValue());
        System.out.println("Average Energy Efficiency (energy per unit of work) : " + bd.toPlainString() + "Wh/MI");

        System.out.println("Total Compute Carbon = " + new Carbon().energyToCarbon(totalEnergy/1000) + "kgCO₂e");

        double operational = new Carbon().energyToCarbon(totalEnergy);
        //System.out.println("Operation Emissions = " + operational + "gCO₂e");

        BigInteger rate = workDone; // let's use the unit of work - its perfect for this (work done)
        double sci = new Sci().calculateSci(operational, embodiedTotal, rate.doubleValue());
        System.out.println("SCI = " + String.format("%.4f", sci) + "mgCO₂e / MI");

        System.out.println("DC Overhead Energy = " + df1.format(new Pue().incrementalEnergyOverhead(totalEnergy)/1000) + "kWh");

        System.out.println();
        return totalEnergy;
    }

    public static PowerServer wattsPerServer(Host host) {
        //CustomCloudletScheduler cs = (CustomCloudletScheduler) host.getVmList().get(0).getCloudletScheduler();
        //double elapsedTime = cs.getHostElapsedTime(host.getId());
        // above won't work, presumably because the VM has been torn down

        double elapsedTime = host.getTotalExecutionTime();

        double endTime = host.getDatacenter().getSimulation().clock();
        final double utilizationPercentMean = elapsedTime / endTime;
        final double watts = host.getPowerModel().getPower(utilizationPercentMean);
        return new PowerServer(utilizationPercentMean, watts);
    }

}