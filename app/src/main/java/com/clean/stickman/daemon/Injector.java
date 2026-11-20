package com.clean.stickman.daemon;

import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Key injector for remapd on Fire OS 5-7, where /dev/uinput is closed. TVSettings's own implementation of the
 * protocol remapd.c speaks (Stickman, MIT, Mohamed Nofal, https://github.com/MohamedNofal/stickman; see
 * THIRD_PARTY.md). It is not a copy of Stickman's injector.
 *
 * Started by remapd: app_process /system/bin --nice-name=stickinj com.clean.stickman.daemon.Injector VENDOR PRODUCT
 * VERSION NAME [EVENT_PATH] (ids in hex). Reads lines on stdin and injects through InputManager:
 *   "linuxKeyCode value"  1 = down, 0 = up. Which Android key it is comes from the remote's key layout file.
 *   "W"                   wake the stick (KEYCODE_WAKEUP).
 *   "D path"              the remote came back as a new device at this evdev path.
 *   "P ..."               pointer lines, ignored: remapd makes a real HID mouse when it can.
 * Prints READY once set up and exits on stdin EOF or after repeated failures.
 */
public final class Injector implements KeyRepeater.Sink {
    private static final String TAG = "stickinj";
    private static final int MAX_FAILS = 5;
    private static final int FLAG_FROM_SYSTEM = 0x8, SOURCE_KEYBOARD = 0x101, VIRTUAL_KEYBOARD = -1;
    private static final int INJECT_ASYNC = 0;
    private static final int KEYCODE_WAKEUP = 224, SCAN_WAKEUP = 143;
    // the platform's own key repeat: first after 500 ms, then every 50 ms; a key held 15 s has lost its key-up
    private static final long REPEAT_DELAY_MS = 500, REPEAT_EVERY_MS = 50, MAX_HOLD_MS = 15_000;

    /** Keys the injector sends the same way whatever the layout says. Everything else follows the layout file. */
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

    /** Layout labels of the voice button: Fire OS only starts voice search for a key from the remote itself. */
    private static final Set<String> VOICE_KEYS = new HashSet<>(Arrays.asList("ALEXA", "VOICE_CMD", "SEARCH", "VOICE_ASSIST", "ASSIST"));

    private final Object inputManager;
    private final Method inject;
    private final Map<Integer, String> layout = new HashMap<>();
    private final Map<Integer, String> generic = new HashMap<>();
    private final Map<Integer, Integer> resolved = new HashMap<>();
    private final KeyRepeater repeater = new KeyRepeater(this, REPEAT_DELAY_MS, REPEAT_EVERY_MS, MAX_HOLD_MS);
    private volatile String voicePath;
    private volatile InputDump.Node voiceNode;
    private int fails;

    private Injector(String[] args) throws Exception {
        Class<?> im = Class.forName("android.hardware.input.InputManager");
        inputManager = im.getMethod("getInstance").invoke(null);
        inject = im.getMethod("injectInputEvent", InputEvent.class, int.class);
        loadLayouts(hex(args, 0), hex(args, 1), hex(args, 2), args.length > 3 ? args[3] : "");
        if (args.length > 4) bindVoice(args[4]);
    }

    // ---- key layouts: which Android key a Linux key code is, as the remote's own layout file says ----

    private void loadLayouts(int vendor, int product, int version, String name) {
        String used = null;
        for (String file : KeyLayoutFile.candidates(vendor, product, version, name)) {
            for (String dir : KeyLayoutFile.DIRS) {
                File kl = new File(dir, file);
                if (used == null && kl.canRead()) {
                    layout.putAll(KeyLayoutFile.parse(read(kl)));
                    used = kl.getPath();
                }
            }
        }
        for (String dir : KeyLayoutFile.DIRS) {
            File kl = new File(dir, "Generic.kl");
            if (generic.isEmpty() && kl.canRead()) generic.putAll(KeyLayoutFile.parse(read(kl)));
        }
        Log.i(TAG, "layout " + used + " (" + layout.size() + " keys, " + generic.size() + " generic)");
    }

    private static String read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static int hex(String[] a, int i) {
        try {
            return Integer.parseInt(a[i], 16);
        } catch (Exception e) {
            return 0;
        }
    }

    private String label(int linux) {
        String n = layout.get(linux);
        return n != null ? n : generic.get(linux);
    }

    // 0 = no Android key for it: the press is dropped
    private int keyCode(int linux) {
        Integer cached = resolved.get(linux);
        if (cached != null) return cached;
        Integer fixed = KEYS.get(linux);
        int code = fixed != null ? fixed : fromLabel(label(linux));
        if (code == 0) Log.w(TAG, "no Android key for linux code " + linux + " (layout label " + label(linux) + ")");
        resolved.put(linux, code);
        return code;
    }

    private static int fromLabel(String label) {
        if (label == null) return 0;
        int code = KeyEvent.keyCodeFromString("KEYCODE_" + label);
        return code != KeyEvent.KEYCODE_UNKNOWN ? code : KeyEvent.keyCodeFromString(label);
    }

    // ---- the voice button: injected as the remote's own device, found through `dumpsys input` ----

    private void bindVoice(String path) {
        voicePath = path;
        voiceNode = null;
        Thread worker = new Thread(() -> {
            InputDump.Node node = lookup(path);
            if (path.equals(voicePath)) voiceNode = node;
        }, "voice-remote");
        worker.setDaemon(true);
        worker.start();
    }

    private static InputDump.Node lookup(String path) {
        String dump = run("dumpsys", "input");
        return dump == null ? null : InputDump.nodeForPath(dump, path);
    }

    // The device still has to be the one that was found: a reconnected remote can come back with a new id
    private static boolean alive(InputDump.Node node) {
        if (node == null) return false;
        InputDevice device = InputDevice.getDevice(node.id);
        return device != null && node.descriptor.equals(device.getDescriptor());
    }

    private int voiceDevice() {
        String path = voicePath;
        if (path == null) return VIRTUAL_KEYBOARD;
        InputDump.Node node = voiceNode;
        if (!alive(node)) {
            node = lookup(path);   // the remote can register just after the kernel's hotplug notice
            voiceNode = node;
        }
        return alive(node) ? node.id : VIRTUAL_KEYBOARD;
    }

    private static String run(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    out.write(readAll(p.getInputStream()));
                } catch (Exception ignored) {
                    // the process ended early; what was read is used
                }
            });
            reader.setDaemon(true);
            reader.start();
            reader.join(3000);
            if (reader.isAlive()) {
                p.destroy();
                return null;
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    // ---- sending ----

    @Override
    public long now() {
        return SystemClock.uptimeMillis();
    }

    @Override
    public synchronized void key(int code, int scan, int action, long downTime, int repeat, int device) {
        boolean ok;
        try {
            KeyEvent ev = new KeyEvent(downTime, now(), action, code, repeat, 0, device, scan, FLAG_FROM_SYSTEM, SOURCE_KEYBOARD);
            ok = Boolean.TRUE.equals(inject.invoke(inputManager, ev, INJECT_ASYNC));
        } catch (Exception e) {
            ok = false;
        }
        if (ok) {
            fails = 0;
            return;
        }
        if (++fails >= MAX_FAILS) {
            Log.e(TAG, "the system refuses key events, giving up");
            System.exit(3);
        }
    }

    private void press(int linux, boolean down) {
        int code = keyCode(linux);
        if (code == 0) return;
        if (down) repeater.down(linux, code, VOICE_KEYS.contains(label(linux)) ? voiceDevice() : VIRTUAL_KEYBOARD);
        else repeater.up(linux, code, VIRTUAL_KEYBOARD);
    }

    // KEYCODE_WAKEUP wakes from sleep and does nothing when the stick is awake
    private void wake() {
        long t = now();
        key(KEYCODE_WAKEUP, SCAN_WAKEUP, KeyRepeater.DOWN, t, 0, VIRTUAL_KEYBOARD);
        key(KEYCODE_WAKEUP, SCAN_WAKEUP, KeyRepeater.UP, t, 0, VIRTUAL_KEYBOARD);
    }

    public static void main(String[] args) {
        try {
            Injector injector = new Injector(args);
            System.out.println("READY");
            System.out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("P")) continue;
                if (line.equals("W")) {
                    injector.wake();
                    continue;
                }
                if (line.startsWith("D ")) {
                    injector.bindVoice(line.substring(2).trim());
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length != 2) continue;
                try {
                    injector.press(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) == 1);
                } catch (NumberFormatException e) {
                    // a line this protocol doesn't have is skipped
                }
            }
        } catch (Exception e) {
            System.exit(2);
        }
    }
}
