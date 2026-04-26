package com.xreous.stepperng.util;
import com.xreous.stepperng.MessageProcessor;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** Rewrites bare {@code $VAR:name$} markers into their cross-sequence form. */
public final class VariableQualifier {
    private static final Pattern BARE_VAR = Pattern.compile("\\$VAR:([^:$]+)\\$");
    private VariableQualifier() {}
    public static Set<String> findBareVarNames(byte[] content) {
        Set<String> names = new HashSet<>();
        if (content == null || content.length == 0) return names;
        Matcher m = BARE_VAR.matcher(new String(content, StandardCharsets.UTF_8));
        while (m.find()) names.add(m.group(1));
        return names;
    }
    public static byte[] qualify(byte[] content, String sequenceName, Set<String> namesToQualify) {
        if (content == null || content.length == 0) return content;
        if (namesToQualify == null || namesToQualify.isEmpty()) return content;
        String s = new String(content, StandardCharsets.UTF_8);
        Matcher m = BARE_VAR.matcher(s);
        StringBuilder out = new StringBuilder(s.length() + 32);
        while (m.find()) {
            String name = m.group(1);
            String replacement = namesToQualify.contains(name)
                    ? "$VAR:" + sequenceName + ":" + name + "$"
                    : m.group(0);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    public static Set<String> findQualifiedVarNamesFor(byte[] content, String sequenceName) {
        Set<String> names = new HashSet<>();
        if (content == null || content.length == 0) return names;
        Matcher m = MessageProcessor.CROSS_SEQ_VAR_PATTERN.matcher(new String(content, StandardCharsets.UTF_8));
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(sequenceName)) names.add(m.group(2));
        }
        return names;
    }
}
