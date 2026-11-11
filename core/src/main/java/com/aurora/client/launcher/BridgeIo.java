package com.aurora.client.launcher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Worker-only NIO operations with a total deadline, including partial progress. */
final class BridgeIo {
    private BridgeIo() {}

    static long deadline() {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LauncherActivityBridge.IO_TIMEOUT_MS);
    }

    static void write(SocketChannel channel, SelectionKey key, byte[] bytes, BooleanSupplier stopped) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long deadline = deadline();
        while (buffer.hasRemaining()) {
            check(stopped, deadline);
            if (channel.write(buffer) == 0) await(key, SelectionKey.OP_WRITE, deadline, stopped);
        }
    }

    static void await(SelectionKey key, int interest, long deadline, BooleanSupplier stopped) throws IOException {
        check(stopped, deadline);
        long remaining = deadline - System.nanoTime();
        key.interestOps(interest);
        key.selector().select(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
        key.selector().selectedKeys().clear();
    }

    private static void check(BooleanSupplier stopped, long deadline) throws IOException {
        if (stopped.getAsBoolean()) throw new IOException("Activity bridge stopped");
        if (System.nanoTime() >= deadline) throw new IOException("Activity bridge I/O timed out");
    }
}
