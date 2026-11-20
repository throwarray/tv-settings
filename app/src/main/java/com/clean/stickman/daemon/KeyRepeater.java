package com.clean.stickman.daemon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Holds keys down and repeats them, as a real keyboard would: the input dispatcher only repeats real devices, and
 * the injector's events are not from one. A key held longer than maxHoldMs is released here, so a lost key-up
 * can't leave a key repeating for ever. Sends happen under the lock, so no repeat can follow a key-up.
 */
final class KeyRepeater {
    static final int DOWN = 0, UP = 1;

    interface Sink {
        void key(int code, int scan, int action, long downTime, int repeat, int device);

        long now();
    }

    private static final class Held {
        final int code, device;
        final long down;
        int count;

        Held(int code, int device, long down) {
            this.code = code;
            this.device = device;
            this.down = down;
        }
    }

    private final Sink sink;
    private final long delayMs, everyMs, maxHoldMs;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "key-repeat");
        t.setDaemon(true);
        return t;
    });
    private final Map<Integer, Held> held = new HashMap<>();
    private final Set<Integer> released = new HashSet<>();

    KeyRepeater(Sink sink, long delayMs, long everyMs, long maxHoldMs) {
        this.sink = sink;
        this.delayMs = delayMs;
        this.everyMs = everyMs;
        this.maxHoldMs = maxHoldMs;
    }

    synchronized void down(int linux, int code, int device) {
        if (held.containsKey(linux)) return;
        released.remove(linux);
        Held h = new Held(code, device, sink.now());
        held.put(linux, h);
        sink.key(code, linux, DOWN, h.down, 0, device);
        timer.schedule(() -> tick(linux, h), delayMs, TimeUnit.MILLISECONDS);
    }

    synchronized void up(int linux, int code, int device) {
        Held h = held.remove(linux);
        // a key already released after its maximum hold has had its key-up
        if (h == null && released.remove(linux)) return;
        sink.key(h != null ? h.code : code, linux, UP, h != null ? h.down : sink.now(), 0, h != null ? h.device : device);
    }

    private synchronized void tick(int linux, Held h) {
        if (held.get(linux) != h) return;
        if (sink.now() - h.down > maxHoldMs) {
            held.remove(linux);
            released.add(linux);
            sink.key(h.code, linux, UP, h.down, 0, h.device);
            return;
        }
        h.count++;
        sink.key(h.code, linux, DOWN, h.down, h.count, h.device);
        timer.schedule(() -> tick(linux, h), everyMs, TimeUnit.MILLISECONDS);
    }
}
