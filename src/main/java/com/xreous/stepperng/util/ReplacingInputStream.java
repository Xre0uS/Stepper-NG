package com.xreous.stepperng.util;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Binary-safe bulk literal replacement. Input bytes round-trip through ISO_8859_1
 * so arbitrary payloads (file uploads, gzipped bodies, non-UTF-8 encodings) survive
 * intact. Replacement values are injected as their UTF-8 byte sequence.
 */
public class ReplacingInputStream {

    public static byte[] applyReplacements(byte[] input, List<Replacement> replacements) {
        if (input == null || input.length == 0 || replacements == null || replacements.isEmpty()) return input;

        Map<String, String> lookup = new HashMap<>(replacements.size() * 2);
        StringBuilder alt = new StringBuilder();
        for (Replacement r : replacements) {
            if (r == null || r.match == null || r.match.isEmpty()) continue;
            if (lookup.putIfAbsent(r.match, r.replace) != null) continue;
            if (alt.length() > 0) alt.append('|');
            alt.append(Pattern.quote(r.match));
        }
        if (alt.length() == 0) return input;

        String haystack = new String(input, StandardCharsets.ISO_8859_1);
        Matcher m = Pattern.compile(alt.toString()).matcher(haystack);
        StringBuilder out = null;
        int last = 0;
        while (m.find()) {
            if (out == null) out = new StringBuilder(haystack.length());
            out.append(haystack, last, m.start());
            out.append(lookup.getOrDefault(m.group(), ""));
            last = m.end();
        }
        if (out == null) return input;
        out.append(haystack, last, haystack.length());
        return out.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    public static class Replacement {
        public final String match, replace;

        public Replacement(String match, String replace) {
            this.match = match;
            if (replace != null && !isAscii(replace)) {
                // Re-encode non-ASCII values as UTF-8 bytes viewed as ISO_8859_1 so the
                // bytes that land on the wire are the intended UTF-8 encoding.
                this.replace = new String(replace.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
            } else {
                this.replace = replace == null ? "" : replace;
            }
        }

        public Replacement(byte[] match, byte[] replace) {
            this.match = new String(match, StandardCharsets.ISO_8859_1);
            this.replace = new String(replace, StandardCharsets.ISO_8859_1);
        }

        private static boolean isAscii(String s) {
            for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 0x7F) return false;
            return true;
        }

        @Override
        public String toString() {
            return "Replacement{match=" + match + ", replace=" + replace + '}';
        }
    }
}



