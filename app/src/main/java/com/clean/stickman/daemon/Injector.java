package com.clean.stickman.daemon;

import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Key injector for remapd on Fire OS 5-7, where /dev/uinput is closed. TVSettings's own implementation of the
 * protocol remapd.c speaks (Stickman, MIT, Mohamed Nofal, https://github.com/MohamedNofal/stickman; see
 * THIRD_PARTY.md). It is not a copy of Stickman's injector.
 *
 * Started by remapd: app_process /system/bin --nice-name=stickinj com.clean.stickman.daemon.Injector VENDOR PRODUCT
 * VERSION NAME (hex). Reads "linuxKeyCode value" lines on stdin (1 = down, 0 = up) and injects the matching Android
 * key event through InputManager. Prints READY once set up and exits on stdin EOF or after repeated failures.
 * Pointer lines ("P ...") are ignored: remapd makes a real HID mouse on Fire OS 7, so the cursor is Android's own.
 */
public final class Injector {
    private static final int MAX_FAILS = 5;
    private static final int ACTION_DOWN = 0, ACTION_UP = 1;
    private static final int FLAG_FROM_SYSTEM = 0x8, SOURCE_KEYBOARD = 0x101, VIRTUAL_KEYBOARD = -1;
    private static final int INJECT_ASYNC = 0;

    /** Linux input code -> Android KeyEvent code, for the keys the Fire TV remote sends. */
    private static final Map<Integer, Integer> KEYS = new HashMap<>();
    static {
        KEYS.put(28, 23);    // KEY_ENTER -> DPAD_CENTER (the remote's OK)
        KEYS.put(103, 19);   // KEY_UP -> DPAD_UP
        KEYS.put(108, 20);   // KEY_DOWN -> DPAD_DOWN
        KEYS.put(105, 21);   // KEY_LEFT -> DPAD_LEFT
        KEYS.put(106, 22);   // KEY_RIGHT -> DPAD_RIGHT
        KEYS.put(102, 3);    // KEY_HOME -> KEYCODE_HOME
        KEYS.put(172, 3);    // KEY_HOMEPAGE -> KEYCODE_HOME
        KEYS.put(158, 4);    // KEY_BACK -> KEYCODE_BACK
        KEYS.put(139, 82);   // KEY_MENU -> KEYCODE_MENU
        KEYS.put(164, 85);   // KEY_PLAYPAUSE -> MEDIA_PLAY_PAUSE
        KEYS.put(163, 87);   // KEY_NEXTSONG -> MEDIA_NEXT
        KEYS.put(165, 88);   // KEY_PREVIOUSSONG -> MEDIA_PREVIOUS
        KEYS.put(168, 89);   // KEY_REWIND -> MEDIA_REWIND
        KEYS.put(208, 90);   // KEY_FASTFORWARD -> MEDIA_FAST_FORWARD
        KEYS.put(115, 24);   // KEY_VOLUMEUP -> VOLUME_UP
        KEYS.put(114, 25);   // KEY_VOLUMEDOWN -> VOLUME_DOWN
        KEYS.put(113, 164);  // KEY_MUTE -> VOLUME_MUTE
        KEYS.put(116, 26);   // KEY_POWER -> POWER
    }

    private final Object inputManager;
    private final Method inject;

    private Injector() throws Exception {
        Class<?> im = Class.forName("android.hardware.input.InputManager");
        inputManager = im.getMethod("getInstance").invoke(null);
        inject = im.getMethod("injectInputEvent", InputEvent.class, int.class);
    }

    private boolean send(int androidCode, int action) {
        long now = SystemClock.uptimeMillis();
        KeyEvent ev = new KeyEvent(now, now, action, androidCode, 0, 0,
                VIRTUAL_KEYBOARD, 0, FLAG_FROM_SYSTEM, SOURCE_KEYBOARD);
        try {
            return (Boolean) inject.invoke(inputManager, ev, INJECT_ASYNC);
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        try {
            Injector injector = new Injector();
            System.out.println("READY");
            System.out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            int fails = 0;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("P")) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 2) continue;
                int code, value;
                try {
                    code = Integer.parseInt(parts[0]);
                    value = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                Integer android = KEYS.get(code);
                if (android == null) continue;
                boolean ok = injector.send(android, value == 1 ? ACTION_DOWN : ACTION_UP);
                fails = ok ? 0 : fails + 1;
                if (fails >= MAX_FAILS) System.exit(3);
            }
        } catch (Exception e) {
            System.exit(2);
        }
    }
}
