package you.jass.betterhitreg.utility;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static you.jass.betterhitreg.hitreg.Hitreg.client;

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