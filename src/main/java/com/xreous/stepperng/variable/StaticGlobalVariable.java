package com.xreous.stepperng.variable;

public class StaticGlobalVariable extends StepVariable {

    public static final String SVAR_PREFIX = "$GVAR:";
    public static final String SVAR_SUFFIX = "$";

    public StaticGlobalVariable() {
        this("", "");
    }

    public StaticGlobalVariable(String identifier, String value) {
        super(identifier);
        this.value = value != null ? value : "";
    }

    @Override
    public String getType() {
        return "Static Global";
    }

    @Override
    public boolean isValid() {
        return identifier != null && !identifier.isEmpty();
    }

    @Override
    public String getValuePreview() {
        return this.value;
    }

    public static String createGvarString(String identifier) {
        return SVAR_PREFIX + identifier + SVAR_SUFFIX;
    }

    public static java.util.regex.Pattern createGvarPattern(String identifier) {
        return java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(SVAR_PREFIX + identifier + SVAR_SUFFIX));
    }

    public static java.util.regex.Pattern createGvarCaptureRegex() {
        return java.util.regex.Pattern.compile("\\$GVAR:([^$]+)\\$");
    }
}

