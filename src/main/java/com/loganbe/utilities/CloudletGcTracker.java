package com.loganbe.utilities;

import org.cloudsimplus.cloudlets.Cloudlet;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks cloudlets using weak references so we can tell when finished cloudlets actually become
 * garbage-collectable without keeping them alive ourselves.
 */
public class CloudletGcTracker {

    private final ReferenceQueue<Cloudlet> queue = new ReferenceQueue<>();
    private final Map<Long, TrackedCloudletReference> liveReferences = new ConcurrentHashMap<>();
    private final AtomicLong registeredCount = new AtomicLong();
    private final AtomicLong collectedCount = new AtomicLong();

    public void register(final Cloudlet cloudlet) {
        drainCollected();
        liveReferences.put(cloudlet.getId(), new TrackedCloudletReference(cloudlet, queue, cloudlet.getId()));
        registeredCount.incrementAndGet();
    }

    public void drainCollected() {
        TrackedCloudletReference ref;
        while ((ref = (TrackedCloudletReference) queue.poll()) != null) {
            liveReferences.remove(ref.cloudletId, ref);
            collectedCount.incrementAndGet();
        }
    }

    public long getRegisteredCount() {
        drainCollected();
        return registeredCount.get();
    }

    public long getCollectedCount() {
        drainCollected();
        return collectedCount.get();
    }

    public long getOutstandingCount() {
        drainCollected();
        return liveReferences.size();
    }

    private static final class TrackedCloudletReference extends WeakReference<Cloudlet> {
        private final long cloudletId;

        private TrackedCloudletReference(final Cloudlet referent, final ReferenceQueue<Cloudlet> queue, final long cloudletId) {
            super(referent, queue);
            this.cloudletId = cloudletId;
        }
    }
}

