package com.tvsettings.app;

import android.os.SystemClock;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * The accessibility cursor's pointer as a long-running process. ShellBinder starts it once through Shizuku's shell
 * (app_process, this APK on the classpath), writes one command per line and reads "ok" or "fail" for each.
 * It injects touch events the way `input motionevent` does, so presses, drags and swipes behave the same.
 *   D x y               press at x, y          M x y   move (the press stays down)
 *   U x y               release at x, y        C x y   press and release at x, y
 *   S x1 y1 x2 y2 ms    swipe, interpolated here
 *   X                   exit
 */
public final class PointerInjector {
    private static final int INJECT_ASYNC = 0;
    private static final long FRAME_MS = 16;

    private final Object inputManager;
    private final Method inject;
    private long downTime;

    private PointerInjector() throws Exception {
        Class<?> im = Class.forName("android.hardware.input.InputManager");
        inputManager = im.getMethod("getInstance").invoke(null);
        inject = im.getMethod("injectInputEvent", InputEvent.class, int.class);
    }

    private boolean send(int action, int x, int y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent ev = MotionEvent.obtain(downTime, now, action, x, y, 0);
        try {
            return Boolean.TRUE.equals(inject.invoke(inputManager, ev, INJECT_ASYNC));
        } catch (Exception e) {
            return false;
        } finally {
            ev.recycle();
        }
    }

    private boolean swipe(int x1, int y1, int x2, int y2, long ms) throws InterruptedException {
        if (!send(MotionEvent.ACTION_DOWN, x1, y1)) return false;
        int steps = (int) Math.max(2, ms / FRAME_MS);
        for (int i = 1; i <= steps; i++) {
            Thread.sleep(FRAME_MS);
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            if (!send(MotionEvent.ACTION_MOVE, x, y)) {
                send(MotionEvent.ACTION_UP, x, y);
                return false;
            }
        }
        return send(MotionEvent.ACTION_UP, x2, y2);
    }

    private static boolean run(PointerInjector p, String[] t) throws Exception {
        switch (t[0]) {
            case "D": return p.send(MotionEvent.ACTION_DOWN, Integer.parseInt(t[1]), Integer.parseInt(t[2]));
            case "M": return p.send(MotionEvent.ACTION_MOVE, Integer.parseInt(t[1]), Integer.parseInt(t[2]));
            case "U": return p.send(MotionEvent.ACTION_UP, Integer.parseInt(t[1]), Integer.parseInt(t[2]));
            case "C": {
                int x = Integer.parseInt(t[1]);
                int y = Integer.parseInt(t[2]);
                return p.send(MotionEvent.ACTION_DOWN, x, y) && p.send(MotionEvent.ACTION_UP, x, y);
            }
            case "S": return p.swipe(Integer.parseInt(t[1]), Integer.parseInt(t[2]),
                    Integer.parseInt(t[3]), Integer.parseInt(t[4]), Long.parseLong(t[5]));
            default: return false;
        }
    }

    public static void main(String[] args) throws Exception {
        PointerInjector p = new PointerInjector();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("READY");
        System.out.flush();
        String line;
        while ((line = in.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.equals("X")) return;
            String[] t = trimmed.split("\\s+");
            boolean ok;
            try {
                ok = run(p, t);
            } catch (Exception e) {
                ok = false;
            }
            System.out.println(ok ? "ok" : "fail");
            System.out.flush();
        }
    }
}
