package com.xreous.stepperng.variable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class DynamicGlobalVariable extends StepVariable {

    public static final String DVAR_PREFIX = "$DVAR:";
    public static final String DVAR_SUFFIX = "$";

    private String regexString;
    private Pattern regex;
    private String hostFilter;
    private Pattern hostPattern;
    private boolean captureFromRequests;

    public DynamicGlobalVariable() {
        this("", null, null);
    }

    public DynamicGlobalVariable(String identifier, String regexString, String hostFilter) {
        this(identifier, regexString, hostFilter, false);
    }

    public DynamicGlobalVariable(String identifier, String regexString, String hostFilter, boolean captureFromRequests) {
        super(identifier);
        setRegex(regexString);
        setHostFilter(hostFilter);
        this.captureFromRequests = captureFromRequests;
    }

    @Override
    public String getType() {
        return "Dynamic Global";
    }

    public void setRegex(String regexString) {
        this.regexString = regexString;
        try {
            if (regexString != null && !regexString.isEmpty()) {
                this.regex = Pattern.compile(regexString, Pattern.DOTALL);
            } else {
                this.regex = null;
            }
        } catch (PatternSyntaxException e) {
            this.regex = null;
        }
    }

    public String getRegexString() {
        return regexString;
    }

    public void setHostFilter(String hostFilter) {
        this.hostFilter = hostFilter;
        try {
            if (hostFilter != null && !hostFilter.isEmpty()) {
                this.hostPattern = Pattern.compile(hostFilter, Pattern.CASE_INSENSITIVE);
            } else {
                this.hostPattern = null;
            }
        } catch (PatternSyntaxException e) {
            this.hostPattern = null;
        }
    }

    public String getHostFilter() {
        return hostFilter;
    }

    public boolean matchesHost(String host) {
        if (hostPattern == null) return true; // No filter = match all
        if (host == null) return false;
        return hostPattern.matcher(host).find();
    }

    public boolean updateFromResponse(String responseText, String host) {
        if (regex == null || responseText == null) return false;
        if (!matchesHost(host)) return false;
        return applyRegex(responseText);
    }

    public boolean updateFromRequest(String requestText, String host) {
        if (!captureFromRequests) return false;
        if (regex == null || requestText == null) return false;
        if (!matchesHost(host)) return false;
        return applyRegex(requestText);
    }

    private boolean applyRegex(String text) {
        Matcher m = regex.matcher(text);
        if (m.find()) {
            String oldValue = this.value;
            if (m.groupCount() > 0) {
                this.value = m.group(1);
            } else {
                this.value = m.group();
            }
            if (this.value != null && !this.value.equals(oldValue)) {
                notifyChanges();
                return true;
            }
        }
        return false;
    }

    public boolean isCaptureFromRequests() {
        return captureFromRequests;
    }

    public void setCaptureFromRequests(boolean captureFromRequests) {
        this.captureFromRequests = captureFromRequests;
    }

    public boolean isValid() {
        return regex != null;
    }

    @Override
    public String getValuePreview() {
        return this.value;
    }

    public static String createDvarString(String identifier) {
        return DVAR_PREFIX + identifier + DVAR_SUFFIX;
    }

    public static Pattern createDvarPattern(String identifier) {
        return Pattern.compile(Pattern.quote(DVAR_PREFIX + identifier + DVAR_SUFFIX));
    }

    public static Pattern createDvarCaptureRegex() {
        return Pattern.compile("\\$DVAR:([^$]+)\\$");
    }
}

