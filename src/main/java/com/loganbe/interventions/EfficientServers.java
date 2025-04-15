package com.loganbe.interventions;

import com.loganbe.Main;
import com.loganbe.power.Power;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.power.models.PowerModelHostSimple;

public class EfficientServers {

    private final double sampleSize = 0.5; // only change half of the servers...

    public EfficientServers(Main simulation) {
        double sample = simulation.hostList.size() * sampleSize;

        int count = 0;
        for(Host host : simulation.hostList) {
            if(count < sample) {
                // half the power...
                final var powerModel = new PowerModelHostSimple(Power.MAX_POWER/2, Power.STATIC_POWER/2);
                powerModel
                        .setStartupPower(Power.HOST_START_UP_POWER)
                        .setShutDownPower(Power.HOST_SHUT_DOWN_POWER);
                host.setPowerModel(powerModel);
                count++;
            } else {
                break;
            }
        }
    }

}