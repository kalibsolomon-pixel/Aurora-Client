package com.aurora.client.launcher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;

/** One connection attempt, one daemon, bounded FIFO. All network operations stay on the worker. */
final class LauncherActivityBridge implements AutoCloseable {
    static final int QUEUE_CAPACITY = 32;
    static final long IO_TIMEOUT_MS = 2000;
    private final ArrayBlockingQueue<Update> updates = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final Consumer<String> diagnostic;
    private final Thread worker;
    private volatile boolean stopped;
    private volatile Selector selector;
    private ActivitySnapshot last;
    private long sequence;

    LauncherActivityBridge(BridgeBootstrap bootstrap, Consumer<String> diagnostic) {
        this.diagnostic = diagnostic;
        publish(ActivitySnapshot.mainMenu());
        worker = new Thread(() -> run(bootstrap), "Aurora-LauncherActivity");
        worker.setDaemon(true);
        worker.start();
    }

    /** Never waits for capacity, serializes, or touches Minecraft off-thread. */
    synchronized void publish(ActivitySnapshot snapshot) {
        if (stopped || snapshot.equals(last)) return;
        if (sequence == Long.MAX_VALUE || !updates.offer(new Update(++sequence, snapshot))) {
            close(); // Losing a transition could leave stale state: terminate instead.
            return;
        }
        last = snapshot;
        wakeup();
    }

    @Override public void close() {
        stopped = true;
        wakeup();
    }

    private void wakeup() {
        Selector current = selector;
        if (current != null) current.wakeup();
    }

    private void run(BridgeBootstrap bootstrap) {
        try (Selector selected = Selector.open(); SocketChannel channel = SocketChannel.open()) {
            selector = selected;
            channel.configureBlocking(false);
            SelectionKey key = channel.register(selected, SelectionKey.OP_CONNECT);
            long deadline = BridgeIo.deadline();
            if (!channel.connect(bootstrap.endpoint)) {
                while (!channel.finishConnect()) await(key, SelectionKey.OP_CONNECT, deadline);
            }
            BridgeIo.write(channel, key, ActivityProtocol.hello(bootstrap), () -> stopped);
            accept(channel, key);
            safeDiagnostic("Aurora launcher activity bridge connected.");
            ByteBuffer unexpected = ByteBuffer.allocate(1);
            while (!stopped) {
                // EOF is observable even while gameplay is quiet. No heartbeat is needed.
                if (channel.read(unexpected) != 0) throw new IOException("Activity bridge closed or unexpected input");
                Update next = updates.poll();
                if (next != null) {
                    BridgeIo.write(channel, key, ActivityProtocol.activity(bootstrap.sessionId, next.sequence(), next.snapshot()), () -> stopped);
                } else {
                    key.interestOps(SelectionKey.OP_READ);
                    // Recheck after setting interest; publish may have raced the previous poll.
                    if (updates.isEmpty() && !stopped) selected.select();
                    selected.selectedKeys().clear();
                }
            }
        } catch (Exception | LinkageError failure) {
            // No exception details: endpoints, values and capabilities must never leak into logs.
            safeDiagnostic("Aurora launcher activity bridge disabled for this session.");
        } finally {
            synchronized (this) {
                stopped = true;
                updates.clear();
                last = null;
            }
            selector = null;
        }
    }

    private void accept(SocketChannel channel, SelectionKey key) throws IOException {
        ByteBuffer line = ByteBuffer.allocate(ActivityProtocol.ACK_BYTES);
        long deadline = BridgeIo.deadline();
        while (true) {
            int n = channel.read(line);
            if (n < 0) throw new IOException("Activity bridge authentication rejected");
            int size = line.position();
            if (size > 0 && line.get(size - 1) == '\n') {
                String ack = new String(line.array(), 0, size, StandardCharsets.UTF_8);
                if (!ActivityProtocol.ACCEPTED.equals(ack)) throw new IOException("Activity bridge acceptance rejected");
                return;
            }
            if (!line.hasRemaining()) throw new IOException("Activity bridge acceptance exceeds limit");
            await(key, SelectionKey.OP_READ, deadline);
        }
    }

    private void await(SelectionKey key, int interest, long deadline) throws IOException {
        BridgeIo.await(key, interest, deadline, () -> stopped);
    }

    private void safeDiagnostic(String message) {
        try { diagnostic.accept(message); } catch (RuntimeException ignored) { /* Optional diagnostics. */ }
    }

    // Package-local observation for deterministic worker termination tests; no game-thread join.
    boolean isStopped() { return stopped; }
    Thread worker() { return worker; }

    private record Update(long sequence, ActivitySnapshot snapshot) {}
}
