package com.loganbe.power;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimpleFixed;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

public class Power {

    // defines the power a Host uses, even if it's idle (in Watts)
    public static final double STATIC_POWER = 520;
    // idle power ~60–70% of max is typical for servers (we'll use 65% of 800W = 520W)

    // max power a Host uses (in Watts)
    public static final int MAX_POWER = 800;

    // host power consumption (in Watts) during startup
    public static final double HOST_START_UP_POWER = 5;

    // host power consumption (in Watts) during shutdown
    public static final double HOST_SHUT_DOWN_POWER = 3;

    public static double sci;

    public static double calculateTotalEnergy(List<Host> hostList, Map<Long, Double> hostUtilisation, BigInteger workDone) {
        double totalPower = 0;
        double totalEnergy = 0;
        double upTimeHours = 0;
        double embodiedTotal = 0;
        double sumUtilisation = 0;
        for (final Host host : hostList) {
            // framework method for utilisation (NOT reliable - overstates utilisation significantly)
            /*
            final HostResourceStats cpuStats = host.getCpuUtilizationStats();
            final double utilizationPercentMean = cpuStats.getMean();

            System.out.println("host : " + host.getId() + ". utilizationPercentMean = " + utilizationPercentMean);

            List<Vm> finishedVMs = host.getVmCreatedList();
            for (Vm vm : finishedVMs) {
                final double vmUtilizationPercentMean = vm.getCpuUtilizationStats().getMean();
                System.out.println("vm : " + vm.getId() + ". vmUtilizationPercentMean = " + vmUtilizationPercentMean);
            }
            final double watts = host.getPowerModel().getPower(utilizationPercentMean);
            */

            // custom utilisation - critical contribution
            double utilisation = hostUtilisation.get(host.getId());
            sumUtilisation += utilisation;
            final double watts = host.getPowerModel().getPower(utilisation);

            totalPower += watts;

            upTimeHours = host.getTotalUpTime() / 60 / 60;
            double energy = watts * upTimeHours;
            totalEnergy += energy;

            HostSimpleFixed hsf = (HostSimpleFixed) host;
            embodiedTotal += hsf.embodiedEmissions;
        }
        DecimalFormat df = new DecimalFormat("#");

        System.out.println("Average Host Utilisation = " + ((sumUtilisation / hostUtilisation.size())*100) + "%");

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
        sci = new Sci().calculateSci(operational, embodiedTotal, rate.doubleValue());
        System.out.println("SCI = " + String.format("%.4f", sci) + "mgCO₂e / MI");

        System.out.println("DC Overhead Energy = " + df1.format(new Pue().incrementalEnergyOverhead(totalEnergy)/1000) + "kWh");

        System.out.println();
        return totalEnergy;
    }

}