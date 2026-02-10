package com.loganbe.power;

import junit.framework.TestCase;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostAbstract;
import org.cloudsimplus.hosts.HostSimpleFixed;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.junit.Assert;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PowerTest extends TestCase {

    public void testCalculateEnergy() {
        List<Pe> peList = new ArrayList<Pe>();
        Pe processor = new PeSimple();
        peList.add(processor);

        Host host = new HostSimpleFixed(peList);

        PowerModelHost powerModel = new PowerModelHostSimple(800,200);
        host.setPowerModel(powerModel);

        double result = Power.calculateEnergy(host, 0.5);
        Assert.assertEquals(500, result, 0);
    }

    public void testCalculateEnergyEfficiencyFromHost() {
        List<Pe> peList = new ArrayList<Pe>();
        Pe processor = new PeSimple();
        peList.add(processor);

        Host host = new HostSimpleFixed(peList);

        PowerModelHost powerModel = new PowerModelHostSimple(800,200);
        host.setPowerModel(powerModel);

        double result = Power.calculateEnergyEfficiencyFromHost(host, BigInteger.valueOf(1000));
        // work done (1000) divided by the energy (200 - at 0% utilisation)
        Assert.assertEquals(5, result, 0);
    }

    public void testCalculateEnergyEfficiency() {
        double result = Power.calculateEnergyEfficiency(3_000_000, BigInteger.valueOf(5_000_000));
        Assert.assertEquals(1.6667, result, 0);
    }

    public void testCalculateTotalEnergy() throws NoSuchFieldException, IllegalAccessException {
        List<Pe> peList = new ArrayList<Pe>();
        Pe processor = new PeSimple();
        peList.add(processor);

        PowerModelHost powerModel1 = new PowerModelHostSimple(800,200);
        PowerModelHost powerModel2 = new PowerModelHostSimple(800,200);
        PowerModelHost powerModel3 = new PowerModelHostSimple(800,200);

        Host host1 = new HostSimpleFixed(peList);
        host1.setId(1);
        host1.setPowerModel(powerModel1);

        Field f1 = HostAbstract.class.getDeclaredField("totalUpTime");
        f1.setAccessible(true);
        f1.set(host1, 3600.0);

        Host host2 = new HostSimpleFixed(peList);
        host2.setId(2);
        host2.setPowerModel(powerModel2);

        Field f2 = HostAbstract.class.getDeclaredField("totalUpTime");
        f2.setAccessible(true);
        f2.set(host2, 3600.0);

        Host host3 = new HostSimpleFixed(peList);
        host3.setId(3);
        host3.setPowerModel(powerModel3);

        Field f3 = HostAbstract.class.getDeclaredField("totalUpTime");
        f3.setAccessible(true);
        f3.set(host3, 3600.0);

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host1);
        hostList.add(host2);
        hostList.add(host3);

        Map<Long, Double> hostUtilisation = new HashMap<Long, Double>();
        hostUtilisation.put(1L, 0.5);
        hostUtilisation.put(2L, 0.5);
        hostUtilisation.put(3L, 0.5);

        BigInteger workDone = BigInteger.valueOf(5_000_000);

        double result = Power.calculateTotalEnergy(hostList, hostUtilisation, workDone);
        Assert.assertEquals(1500, result, 0);
    }

}