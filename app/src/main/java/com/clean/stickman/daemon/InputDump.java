package com.clean.stickman.daemon;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a device in the "Event Hub State" part of `dumpsys input`: the Android device id and descriptor of the
 * device behind an evdev node. TVSettings's own parser, written against the public dump layout (a block per device,
 * with Path and Descriptor lines).
 */
final class InputDump {
    private static final Pattern DEVICE = Pattern.compile("    (-?\\d+): .*");

    static final class Node {
        final int id;
        final String descriptor;

        Node(int id, String descriptor) {
            this.id = id;
            this.descriptor = descriptor;
        }
    }

    private InputDump() {}

    /** The device whose Path line is exactly {@code path}, or null. Reads nothing outside the Event Hub section. */
    static Node nodeForPath(String dump, String path) {
        boolean inHub = false, selected = false;
        int id = -1;
        for (String line : dump.split("\n")) {
            if (!inHub) {
                inHub = "Event Hub State:".equals(line.trim());
                continue;
            }
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0))) break;
            Matcher header = DEVICE.matcher(line);
            if (header.matches()) {
                id = Integer.parseInt(header.group(1));
                if (line.endsWith("(aka device 0 - built-in keyboard)")) id = 0;
                selected = false;
                continue;
            }
            String field = line.trim();
            if (field.startsWith("Path: ")) selected = field.substring(6).equals(path);
            if (selected && id >= 0 && field.startsWith("Descriptor: ")) return new Node(id, field.substring(12));
        }
        return null;
    }
}
