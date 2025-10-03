package com.loganbe.utilities;

import org.cloudsimplus.core.CloudSimEntity;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.core.events.SimEvent;

import java.util.function.DoubleConsumer;

public class Sampler extends CloudSimEntity {

    private static final int SAMPLING = 1000;
    private final double interval;      // e.g., 0.1
    private final double endTime;       // e.g., 3600
    private final DoubleConsumer onSample; // called each tick with current sim time

    public Sampler(final Simulation sim, final double interval, final double endTime,
                   final DoubleConsumer onSample) {
        super(sim);
        this.interval = interval;
        this.endTime = endTime;
        this.onSample = onSample;
    }

    @Override
    protected void startInternal() {
        // first sample at t=0; use 'interval' instead if you want to skip t=0
        schedule(0.0, SAMPLING);   // schedule-to-self
    }

    @Override
    public void processEvent(final SimEvent ev) {
        if (ev.getTag() != SAMPLING) return;

        final double t = getSimulation().clock();
        onSample.accept(t);                    // <-- do your CPU-util sampling here

        if (t + interval <= endTime + 1e-9) {  // FP guard
            schedule(interval, SAMPLING);      // re-arm next tick
        }
    }

    //@Override
    //protected void shutdownInternal() { /* no-op */ }
}