package com.clean.stickman.daemon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Android key layout (.kl) lookup for the key injector. TVSettings's own code, written from the public .kl format:
 * lines of "key SCANCODE LABEL [FLAGS]", where the label is a KEYCODE_ name without its prefix.
 */
final class KeyLayoutFile {
    /** Where Android looks for layouts, in its order. */
    static final String[] DIRS = {
            "/odm/usr/keylayout", "/vendor/usr/keylayout", "/system/usr/keylayout", "/data/system/devices/keylayout"};

    private KeyLayoutFile() {}

    /** File names to try for a device, most specific first, as Android picks them; Generic.kl last. */
    static List<String> candidates(int vendor, int product, int version, String deviceName) {
        List<String> names = new ArrayList<>();
        if (vendor != 0 || product != 0) {
            if (version != 0) names.add(String.format(Locale.US, "Vendor_%04x_Product_%04x_Version_%04x.kl", vendor, product, version));
            names.add(String.format(Locale.US, "Vendor_%04x_Product_%04x.kl", vendor, product));
        }
        if (deviceName != null && !deviceName.isEmpty()) {
            names.add(deviceName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".kl");
        }
        names.add("Generic.kl");
        return names;
    }

    /** Scan code to key label. Usage, axis, led and sensor lines, comments and flags are ignored. */
    static Map<Integer, String> parse(String text) {
        Map<Integer, String> keys = new HashMap<>();
        for (String raw : text.split("\n")) {
            String line = raw;
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            String[] t = line.trim().split("\\s+");
            if (t.length < 3 || !t[0].equals("key")) continue;
            try {
                keys.put(Integer.decode(t[1]), t[2]);
            } catch (NumberFormatException ignored) {
                // a line this parser doesn't understand is skipped
            }
        }
        return keys;
    }
}
