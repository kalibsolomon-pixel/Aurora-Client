package com.aurora.client.hitreg.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.aurora.client.hitreg.Hitreg.client;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 */
public class Scheduler {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public static void schedule(Runnable task) {
        if (client == null) return;
        client.execute(task);
    }

    public static void schedule(long delay, Runnable task) {
        if (client == null) return;
        if (delay == 0) client.execute(task);
        else SCHEDULER.schedule(() -> client.execute(task), delay, TimeUnit.MILLISECONDS);
    }
}