package com.xreous.stepperng.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegexGenerator {

    private static final String REGEX_SPECIAL = "\\[](){}.*+?^$|";

    public static String escapeRegex(String literal) {
        StringBuilder sb = new StringBuilder();
        for (char c : literal.toCharArray()) {
            if (REGEX_SPECIAL.indexOf(c) >= 0) sb.append('\\').append(c);
            else sb.append(c);
        }
        return sb.toString();
    }

    public static String generateRegex(String fullText, String selectedText, int selectionStart) {
        if (selectedText == null || selectedText.isEmpty()) return "";
        if (fullText == null || fullText.isEmpty()) return "";

        int trimStart = 0;
        int trimEnd = selectedText.length();
        while (trimStart < trimEnd && (selectedText.charAt(trimStart) == '\r' || selectedText.charAt(trimStart) == '\n'))
            trimStart++;
        while (trimEnd > trimStart && (selectedText.charAt(trimEnd - 1) == '\r' || selectedText.charAt(trimEnd - 1) == '\n'))
            trimEnd--;
        if (trimStart >= trimEnd) return "";
        selectionStart += trimStart;
        selectedText = selectedText.substring(trimStart, trimEnd);

        int selectionEnd = selectionStart + selectedText.length();
        int lineEnd = findLineEnd(fullText, selectionStart);
        boolean selectionWithinSingleLine = selectionEnd <= lineEnd;

        if (selectionWithinSingleLine) {
            return generateSameLineRegex(fullText, selectedText, selectionStart, selectionEnd);
        } else {
            return generateMultiLineRegex(fullText, selectedText, selectionStart, selectionEnd);
        }
    }

    private static String generateSameLineRegex(String fullText, String selectedText,
                                                  int absStart, int absEnd) {
        String prefix = findNearestPrefix(fullText, absStart);
        String suffix = findNearestSuffix(fullText, absEnd);

        if (!prefix.isEmpty() && !suffix.isEmpty()) {
            return escapeRegex(prefix) + "([^\\r\\n]*?)" + escapeRegex(suffix);
        }

        if (!prefix.isEmpty()) {
            int lineEnd = findLineEnd(fullText, absEnd);
            String remaining = fullText.substring(absEnd, lineEnd);
            if (remaining.trim().isEmpty()) {
                return escapeRegex(prefix) + "([^\\r\\n]+)";
            }
            return escapeRegex(prefix) + "([^\\r\\n]*?)\\s";
        }

        if (!suffix.isEmpty()) {
            return "([^\\s]+?)" + escapeRegex(suffix);
        }

        return generateFromStructuralContext(fullText, selectedText, absStart, absEnd);
    }

    private static String generateMultiLineRegex(String fullText, String selectedText,
                                                   int absStart, int absEnd) {
        String prefix = findNearestPrefix(fullText, absStart);
        String suffix = findNearestSuffix(fullText, absEnd);

        if (!prefix.isEmpty() && !suffix.isEmpty()) {
            return escapeRegex(prefix) + "([\\s\\S]*?)" + escapeRegex(suffix);
        }
        if (!prefix.isEmpty()) {
            String nextAnchor = grabNextNonEmptyLinePrefix(fullText, absEnd, 20);
            if (!nextAnchor.isEmpty()) {
                return escapeRegex(prefix) + "([\\s\\S]*?)" + escapeRegex(nextAnchor);
            }
            return escapeRegex(prefix) + "([\\s\\S]*?)\\r?\\n";
        }
        return generateFromStructuralContext(fullText, selectedText, absStart, absEnd);
    }

    private static String generateFromStructuralContext(String fullText, String selectedText,
                                                         int absStart, int absEnd) {
        String prevAnchor = grabPreviousLineEnd(fullText, absStart, 30);
        String nextAnchor = grabNextNonEmptyLinePrefix(fullText, absEnd, 30);

        if (!prevAnchor.isEmpty() && !nextAnchor.isEmpty()) {
            String capture = selectedText.contains("\n") ? "([\\s\\S]*?)" : "([^\\r\\n]+?)";
            return escapeRegex(prevAnchor) + "\\s+" + capture + "\\s*" + escapeRegex(nextAnchor);
        }
        if (!prevAnchor.isEmpty()) {
            return escapeRegex(prevAnchor) + "\\s+([^\\r\\n]+)";
        }
        if (!nextAnchor.isEmpty()) {
            return "([^\\r\\n]+?)\\s*" + escapeRegex(nextAnchor);
        }
        return "";
    }

    private static String findNearestPrefix(String fullText, int selStart) {
        int lineStart = findLineStart(fullText, selStart);
        String before = fullText.substring(lineStart, selStart);
        if (before.isEmpty()) return "";

        int bestKeyStart = -1;
        for (int i = before.length() - 1; i >= 0; i--) {
            char c = before.charAt(i);
            if (c == '"' || c == '\'') {
                String sub = before.substring(0, i + 1);
                int keyMatch = findJsonKeyAnchor(sub);
                if (keyMatch >= 0) {
                    bestKeyStart = keyMatch;
                    break;
                }
            }
            if (c == ':') {
                // Try JSON key anchor first (handles "key":value for unquoted values like numbers)
                String sub = before.substring(0, i + 1);
                int keyMatch = findJsonKeyAnchor(sub);
                if (keyMatch >= 0) {
                    bestKeyStart = keyMatch;
                    break;
                }
                int headerMatch = findHeaderAnchor(sub, lineStart == 0 || fullText.charAt(lineStart - 1) == '\n');
                if (headerMatch >= 0) {
                    bestKeyStart = headerMatch;
                    break;
                }
            }
            if (c == '>') {
                int tagStart = findTagAnchor(before, i);
                if (tagStart >= 0) {
                    bestKeyStart = tagStart;
                    break;
                }
            }
            if (c == '=') {
                int eqAnchor = findAssignmentAnchor(before, i);
                if (eqAnchor >= 0) {
                    bestKeyStart = eqAnchor;
                    break;
                }
            }
        }

        if (bestKeyStart >= 0) {
            String anchor = before.substring(bestKeyStart);
            // If the key part of the anchor is very short, extend backward for uniqueness
            if (anchorKeyLength(anchor) < 3 && bestKeyStart > 0) {
                String extended = extendAnchorBackward(before, bestKeyStart);
                if (extended != null) return extended;
            }
            return anchor;
        }

        int lastDelim = -1;
        for (int i = before.length() - 1; i >= 0; i--) {
            char c = before.charAt(i);
            if (c == ',' || c == ';' || c == '&' || c == '?' || c == '\t') {
                lastDelim = i;
                break;
            }
        }
        if (lastDelim >= 0 && lastDelim < before.length() - 1) {
            return before.substring(lastDelim + 1);
        }

        if (before.length() <= 60) {
            return before;
        }
        return "";
    }

    private static int findJsonKeyAnchor(String before) {
        // Match "key": optionally followed by opening quote — find the LAST occurrence (closest to selection)
        Pattern jsonKey = Pattern.compile("\"([A-Za-z_][A-Za-z0-9_.-]*)\"\\s*:\\s*\"?\\s*$");
        Matcher m = jsonKey.matcher(before);
        if (m.find()) return m.start();

        Pattern jsonKeySingle = Pattern.compile("'([A-Za-z_][A-Za-z0-9_.-]*)'\\s*:\\s*'?\\s*$");
        m = jsonKeySingle.matcher(before);
        if (m.find()) return m.start();

        return -1;
    }

    private static int findHeaderAnchor(String before, boolean isStartOfLine) {
        Pattern header = Pattern.compile("^([A-Za-z][A-Za-z0-9-]*):\\s*$");
        Matcher m = header.matcher(before.trim());
        if (m.find()) {
            int trimOffset = before.length() - before.stripLeading().length();
            return trimOffset;
        }
        Pattern headerEnd = Pattern.compile("([A-Za-z][A-Za-z0-9-]*):\\s*$");
        m = headerEnd.matcher(before);
        if (m.find()) return m.start();
        return -1;
    }

    private static int findTagAnchor(String before, int closePos) {
        for (int i = closePos - 1; i >= 0; i--) {
            if (before.charAt(i) == '<') {
                return i;
            }
        }
        return closePos;
    }

    private static int findAssignmentAnchor(String before, int eqPos) {
        Pattern assign = Pattern.compile("([A-Za-z_][A-Za-z0-9_.-]*)=\\s*\"?\\s*$");
        Matcher m = assign.matcher(before.substring(0, eqPos + 1));
        if (m.find()) return m.start();
        return -1;
    }

    private static String findNearestSuffix(String fullText, int selEnd) {
        int lineEnd = findLineEnd(fullText, selEnd);
        String after = fullText.substring(selEnd, lineEnd);
        if (after.isEmpty()) return "";

        for (int i = 0; i < after.length(); i++) {
            char c = after.charAt(i);
            if (c == '"' || c == '\'' || c == '<' || c == ',' || c == ';'
                    || c == '}' || c == ')' || c == ']' || c == '&') {
                int end = i + 1;
                if ((c == '"' || c == '\'') && end < after.length()) {
                    char next = after.charAt(end);
                    if (next == ',' || next == '}' || next == ']') {
                        end++;
                        if (next == ',' && end < after.length() && after.charAt(end) == '"') {
                            int keyEnd = after.indexOf('"', end + 1);
                            if (keyEnd > 0 && keyEnd - end < 30) {
                                end = keyEnd + 1;
                            }
                        }
                    }
                }
                return after.substring(0, end);
            }
        }

        if (after.length() <= 30) return after;
        return "";
    }

    static int findLineStart(String text, int pos) {
        for (int i = pos - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n' || text.charAt(i) == '\r') return i + 1;
        }
        return 0;
    }

    static int findLineEnd(String text, int pos) {
        for (int i = pos; i < text.length(); i++) {
            if (text.charAt(i) == '\r' || text.charAt(i) == '\n') return i;
        }
        return text.length();
    }

    private static String grabPreviousLineEnd(String text, int pos, int maxChars) {
        int cursor = findLineStart(text, pos);
        for (int attempt = 0; attempt < 5 && cursor > 0; attempt++) {
            int prevLineEnd = cursor - 1;
            if (prevLineEnd > 0 && text.charAt(prevLineEnd - 1) == '\r') prevLineEnd--;
            int prevLineStart = findLineStart(text, prevLineEnd);
            String prev = text.substring(prevLineStart, prevLineEnd);
            if (!prev.trim().isEmpty()) {
                int take = Math.min(prev.length(), maxChars);
                return prev.substring(prev.length() - take);
            }
            cursor = prevLineStart;
        }
        return "";
    }

    private static String grabNextNonEmptyLinePrefix(String text, int pos, int maxChars) {
        int cursor = findLineEnd(text, pos);
        for (int attempt = 0; attempt < 5 && cursor < text.length(); attempt++) {
            int next = cursor;
            if (next < text.length() && text.charAt(next) == '\r') next++;
            if (next < text.length() && text.charAt(next) == '\n') next++;
            if (next >= text.length()) return "";
            int nextEnd = findLineEnd(text, next);
            String line = text.substring(next, nextEnd);
            if (!line.trim().isEmpty()) {
                int take = Math.min(line.length(), maxChars);
                return line.substring(0, take);
            }
            cursor = nextEnd;
        }
        return "";
    }

    /** Return the length of the leading alphanumeric/identifier portion of an anchor string. */
    private static int anchorKeyLength(String anchor) {
        // Strip leading quotes
        int i = 0;
        while (i < anchor.length() && (anchor.charAt(i) == '"' || anchor.charAt(i) == '\'')) i++;
        int start = i;
        while (i < anchor.length() && (Character.isLetterOrDigit(anchor.charAt(i))
                || anchor.charAt(i) == '_' || anchor.charAt(i) == '-' || anchor.charAt(i) == '.')) {
            i++;
        }
        return i - start;
    }

    /**
     * Extend an anchor backward past the previous delimiter to include more unique context.
     * For example, for before="oneTimeToken":"53ea...","t":" and bestKeyStart pointing to "t",
     * this will include the preceding key-value pair tail to form a longer, more unique prefix.
     */
    private static String extendAnchorBackward(String before, int bestKeyStart) {
        // Scan backward from bestKeyStart to find a preceding delimiter
        int scan = bestKeyStart - 1;
        // Skip whitespace
        while (scan >= 0 && Character.isWhitespace(before.charAt(scan))) scan--;
        if (scan < 0) return null;

        // Look for the preceding comma, semicolon, opening brace, etc.
        // Walk back up to 60 chars to find a useful chunk
        int limit = Math.max(0, bestKeyStart - 60);
        int extStart = -1;

        for (int i = scan; i >= limit; i--) {
            char c = before.charAt(i);
            if (c == ',' || c == ';' || c == '{' || c == '[' || c == '&' || c == '\t') {
                extStart = i;
                break;
            }
        }

        if (extStart >= 0) {
            String extended = before.substring(extStart);
            if (extended.length() > 80) {
                // Too long — trim from the delimiter
                extended = before.substring(bestKeyStart > 20 ? bestKeyStart - 20 : 0);
            }
            return extended;
        }

        // If no delimiter found, just take more chars from further back
        int take = Math.min(bestKeyStart, 30);
        if (take > 0) {
            return before.substring(bestKeyStart - take);
        }
        return null;
    }
}
