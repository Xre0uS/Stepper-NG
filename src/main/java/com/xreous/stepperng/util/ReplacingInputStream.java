package com.xreous.stepperng.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Bulk string replacement utility.
 */
public class ReplacingInputStream {

    public static byte[] applyReplacements(byte[] input, List<Replacement> replacements) {
        if (replacements == null || replacements.isEmpty()) return input;
        String result = new String(input, StandardCharsets.UTF_8);
        boolean changed = false;
        for (Replacement r : replacements) {
            if (result.contains(r.match)) {
                result = result.replace(r.match, r.replace);
                changed = true;
            }
        }
        return changed ? result.getBytes(StandardCharsets.UTF_8) : input;
    }

    public static class Replacement {
        public final String match, replace;

        public Replacement(String match, String replace) {
            this.match = match;
            this.replace = replace;
        }

        public Replacement(byte[] match, byte[] replace) {
            this.match = new String(match, StandardCharsets.UTF_8);
            this.replace = new String(replace, StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return "Replacement{match=" + match + ", replace=" + replace + '}';
        }
    }
}