package com.loganbe.power;

import com.loganbe.utilities.Maths;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static final Logger LOGGER = LoggerFactory.getLogger(Power.class.getSimpleName());

    public static double calculateEnergy(Host host, Double hostUtilisation) {
        return host.getPowerModel().getPower(hostUtilisation);
    }

    public static double calculateEnergyEfficiencyFromHost(Host host, BigInteger workDone) {
        if(workDone.intValue() == 0) {
            return 0;
        }
        double hostUtilisation = host.getCpuPercentUtilization();
        double energy = host.getPowerModel().getPower(hostUtilisation);

        return calculateEnergyEfficiency(energy, workDone);
    }

    /**
     * energy efficiency ratio;
     * work done per unit of energy
     * (i.e., MIs per Wh)
     *
     * @param energy
     * @param workDone
     * @return
     */
    public static double calculateEnergyEfficiency(double energy, BigInteger workDone) {
        if(workDone.intValue() == 0) {
            return 0;
        }
        BigDecimal bd = BigDecimal.valueOf(workDone.doubleValue() / energy);
        return bd.doubleValue();
    }

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

            // FIXME reintroduce, disabling temporarily
            HostSimpleFixed hsf = (HostSimpleFixed) host;
            embodiedTotal += hsf.embodiedEmissions;
        }
        DecimalFormat df = new DecimalFormat("#");

        double utilisation = Maths.quickRound((sumUtilisation / hostUtilisation.size())*100);
        LOGGER.info(utilisation + "% = Average Host Utilisation");

        // FIXME new (old) utilisation logic
        for (final Host host : hostList) {
            //LOGGER.info(host.getCpuPercentUtilization() + "% = OLD Host Utilisation");
            //LOGGER.info(host.getCpuUtilizationStats().getMin() + " = OLD Host Utilisation (min)");
            //LOGGER.info(host.getCpuUtilizationStats().getMax() + " = OLD Host Utilisation (max)");
            LOGGER.info(host.getCpuUtilizationStats().getMean() + " = OLD Host Utilisation (mean)");
            LOGGER.info(host.getCpuUtilizationStats().count() + " = OLD Host Utilisation (count)");
            // so I haven't really fixed this! why is it always 1 (100%)? could it because of my 'fix' in Host?
        }

        //System.out.println("Total Power = " + df.format(totalPower) + "W");

        LOGGER.info("Energy Consumption = " + df.format(totalPower) + "Wh (i.e. per hour)");

        DecimalFormat df1 = new DecimalFormat("#.##");
        LOGGER.info("Total Compute Energy = " + df.format(totalEnergy) + "Wh (" + df1.format(totalEnergy/1000) + "kWh) consumed in " + df1.format(upTimeHours) + "hr(s)");

        // energy efficiency - work done (MIPS), per unit of energy consumed
        double energyEfficiency = calculateEnergyEfficiency(totalEnergy, workDone);
        LOGGER.info("Average Energy Efficiency (work per unit of energy) : " + energyEfficiency + "MI/Wh");

        LOGGER.info("Total Compute Carbon = " + new Carbon().energyToCarbon(totalEnergy/1000) + "kgCO₂e");

        double operational = new Carbon().energyToCarbon(totalEnergy);
        LOGGER.info("Operation Emissions = " + operational + "gCO₂e");

        BigInteger rate = workDone; // let's use the unit of work - its perfect for this (work done)
        //LOGGER.info("embodiedTotal = " + embodiedTotal);
        //LOGGER.info("rate = " + rate.doubleValue());
        sci = new Sci().calculateSci(operational, embodiedTotal, rate.doubleValue());
        LOGGER.info("SCI = " + String.format("%.4f", sci) + "mgCO₂e / MI");

        LOGGER.info("DC Overhead Energy = " + df1.format(new Pue().incrementalEnergyOverhead(totalEnergy)/1000) + "kWh");

        return totalEnergy;
    }

}