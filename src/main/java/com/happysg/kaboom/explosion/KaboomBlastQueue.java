package com.happysg.kaboom.explosion;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;

import static com.happysg.kaboom.CreateKaboom.MODID;

/**
 * Applies Kaboom terrain plans only on the logical server, under both a per-blast item cap and a
 * total CPU-time budget shared by every queued blast.
 */
@EventBusSubscriber(modid = MODID)
public final class KaboomBlastQueue {

    /*
     * Tune from profiling, not intuition. The current cap limits one large blast from monopolizing
     * a tick while the global deadline caps all active Kaboom blasts together.
     */
    private static final int MAX_CHANGES_PER_BLAST_TICK = 20_000;
    private static final long MAX_TOTAL_WORK_PER_TICK_NANOS = 40_500_000L; // 2.5 ms

    private static final Deque<KaboomExplosionEngine.QueuedBlast> QUEUED_BLASTS =
            new ArrayDeque<>();

    private KaboomBlastQueue() {
    }

    static void enqueue(KaboomExplosionEngine.QueuedBlast blast) {
        QUEUED_BLASTS.addLast(blast);
    }

    @SubscribeEvent
    public static void processQueuedBlasts(ServerTickEvent.Post event) {
        if (QUEUED_BLASTS.isEmpty() || !event.hasTime()) {
            return;
        }

        long deadline = System.nanoTime() + MAX_TOTAL_WORK_PER_TICK_NANOS;
        int turns = QUEUED_BLASTS.size();

        /*
         * One turn per already-queued blast gives round-robin fairness. Newly queued work waits
         * until the next server tick rather than extending this tick indefinitely.
         */
        while (turns-- > 0
                && !QUEUED_BLASTS.isEmpty()
                && System.nanoTime() < deadline) {

            KaboomExplosionEngine.QueuedBlast blast =
                    QUEUED_BLASTS.removeFirst();

            if (blast.process(
                    deadline,
                    MAX_CHANGES_PER_BLAST_TICK
            )) {
                blast.complete();
            } else {
                QUEUED_BLASTS.addLast(blast);
            }
        }
    }
}
