package com.loganbe;

public class Scratch {

    // OK, WE'VE FOUND THE PROBLEM/BUG!
    // once the clock turns 1, the interval drops to 0 and the whole thing blows up, it should still be 0.1!

    double clock = 3.2; // problem
    //double clock = 0.9; // not a problem

    double schedulingInterval = 0.1;
    double nextFinishingCloudletTime = 39; // seconds till finish (total, or remaining?)

    final double time = Math.floor(clock);
    final double mod = time % schedulingInterval;

    final double EPSILON = 1E-9; // Small tolerance value for precision errors

    /* If a scheduling interval is set, ensures the next time that Cloudlets' processing
     * are updated is multiple of the scheduling interval.
     * If there is an event happening before such a time, then the event
     * will be scheduled as usual. Otherwise, the update
     * is scheduled to the next time multiple of the scheduling interval.*/

    public static void main(String[] args) {
        new Scratch();
    }

    public Scratch() {
        //final double delay = mod == 0 ? schedulingInterval : (time - mod + schedulingInterval) - time;

        // break it down to ease understanding...
        System.out.println("time = " + time);
        System.out.println("mod = " + mod); // remainder when time is divided by scheduling interval

        // if mod is zero return the scheduling interval
        // it will always be zero when time is below 1 (because time is then zero, so you div by zero)
        // so you end up using the scheduling interval (why would you ever want to use anything else?)

        // but it's not when clock is 1 or greater, so then we use this other piece of logic;
        // (time - mod + schedulingInterval) - time)
        //System.out.println("CHECK : " + ((time - mod + schedulingInterval) - time));
        // note that this equals 0.1 (i.e. matches interval), when time is 0
        // but always equals zero when time is 1 or greater?

        //Last aligned time: time - mod = 23 - 3 = 20.
        //Next aligned time: time - mod + schedulingInterval = 20 + 10 = 30.
        System.out.println("LAST ALIGNED TIME = " + (time - mod));
        System.out.println("NEXT ALIGNED TIME = " + (time - mod + schedulingInterval));
        System.out.println("NEXT ALIGNED TIME = " + ((time - mod + schedulingInterval)-time));

        final double delay = mod == 0 ? schedulingInterval : (time - mod + schedulingInterval) - time;
        System.out.println("delay BROKEN (expect 0.1) " + delay);

        // the last bit ends up being 0, when over 1 (in those cases we want to use the scheduling interval (not zero!)
        // but then over 3 it ends up being not quite zero
        // bigger numbers are OK. so what's special about 3!?! is this just floating point precision!?

        double delaySubComponent = (time - mod + schedulingInterval) - time;
        //if(delaySubComponent == 0) { // or very close to zero?!
        //    delaySubComponent = schedulingInterval;
        //}
        // Check if delaySubComponent is close to 0
        if (Math.abs(delaySubComponent) < EPSILON) {
            delaySubComponent = schedulingInterval;
        }
        final double delayNew = mod == 0 ? schedulingInterval : delaySubComponent;
        System.out.println("delay FIXED (expect 0.1) " + delayNew);

        // MUCH BETTER, WORKS UP TO 2.9, THEN BREAKS AT 3
    }

}
