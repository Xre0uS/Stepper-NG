package com.xreous.stepperng.condition;

import com.xreous.stepperng.step.StepExecutionInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class StepCondition {

    public enum ConditionType {
        REGEX_MATCH,
        STATUS_CODE,
        ALWAYS;

        @Override
        public String toString() {
            return switch (this) {
                case REGEX_MATCH -> "Response body";
                case STATUS_CODE -> "Status line";
                case ALWAYS -> "Always";
            };
        }
    }

    public enum MatchMode {
        MATCHES,
        DOES_NOT_MATCH;

        @Override
        public String toString() {
            return this == MATCHES ? "matches" : "does not match";
        }
    }

    private ConditionType type;
    private String pattern;
    private transient Pattern compiledPattern;
    private MatchMode matchMode;
    private ConditionFailAction action;
    private String gotoTarget;
    private int retryCount;
    private long retryDelayMs;
    private ConditionFailAction elseAction;
    private String elseGotoTarget;

    public StepCondition() {
        this.type = ConditionType.REGEX_MATCH;
        this.pattern = "";
        this.matchMode = MatchMode.MATCHES;
        this.action = ConditionFailAction.CONTINUE;
        this.gotoTarget = "";
        this.retryCount = 0;
        this.retryDelayMs = 500;
        this.elseAction = ConditionFailAction.CONTINUE;
        this.elseGotoTarget = "";
    }

    public StepCondition(ConditionType type, String pattern, MatchMode matchMode,
                         ConditionFailAction action, String gotoTarget,
                         int retryCount, long retryDelayMs) {
        this.type = type;
        this.pattern = pattern;
        this.matchMode = matchMode;
        this.action = action;
        this.gotoTarget = gotoTarget;
        this.retryCount = retryCount;
        this.retryDelayMs = retryDelayMs;
        this.elseAction = ConditionFailAction.CONTINUE;
        this.elseGotoTarget = "";
    }

    /**
     * Returns true when the condition is triggered (pattern matched / not matched per matchMode).
     * When true, the action should fire.
     * When false, the step proceeds normally to the next step.
     */
    public boolean evaluate(StepExecutionInfo executionInfo) {
        if (executionInfo == null || executionInfo.getRequestResponse() == null
                || executionInfo.getRequestResponse().response() == null) {
            return matchMode == MatchMode.DOES_NOT_MATCH;
        }

        if (type == ConditionType.ALWAYS) return true;

        byte[] responseBytes = executionInfo.getRequestResponse().response().toByteArray().getBytes();

        boolean patternFound;
        if (type == ConditionType.STATUS_CODE) {
            // Only convert the status line to String, not the entire response body
            int lineEnd = 0;
            for (int i = 0; i < responseBytes.length; i++) {
                if (responseBytes[i] == '\r' || responseBytes[i] == '\n') { lineEnd = i; break; }
            }
            if (lineEnd == 0) lineEnd = Math.min(responseBytes.length, 128);
            String statusLine = new String(responseBytes, 0, lineEnd);
            patternFound = matchesPattern(statusLine);
        } else {
            patternFound = matchesPattern(new String(responseBytes));
        }

        return matchMode == MatchMode.MATCHES ? patternFound : !patternFound;
    }

    private boolean matchesPattern(String text) {
        if (pattern == null || pattern.isEmpty()) return true;
        try {
            Pattern p = ensureCompiledPattern();
            if (p == null) return false;
            return p.matcher(text).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private Pattern ensureCompiledPattern() {
        if (compiledPattern == null && pattern != null && !pattern.isEmpty()) {
            try {
                compiledPattern = Pattern.compile(pattern, Pattern.DOTALL);
            } catch (PatternSyntaxException e) {
                return null;
            }
        }
        return compiledPattern;
    }

    public boolean isConfigured() {
        return type == ConditionType.ALWAYS || (pattern != null && !pattern.isEmpty());
    }

    public String getSummary() {
        if (!isConfigured()) return "None";
        StringBuilder sb = new StringBuilder("If ");
        if (type == ConditionType.ALWAYS) {
            sb.append("always");
        } else {
            sb.append(type == ConditionType.STATUS_CODE ? "status" : "response");
            String pat = pattern.length() > 20 ? pattern.substring(0, 20) + "…" : pattern;
            sb.append(" ").append(matchMode == MatchMode.MATCHES ? "matches" : "doesn't match");
            sb.append(" /").append(pat).append("/");
        }
        sb.append(" → ").append(action);
        if (action == ConditionFailAction.GOTO_STEP && gotoTarget != null && !gotoTarget.isEmpty()) {
            sb.append(" (").append(gotoTarget).append(")");
        }
        if (retryCount > 0) sb.append(", retry ").append(retryCount).append("x");
        if (type != ConditionType.ALWAYS && elseAction != null && elseAction != ConditionFailAction.CONTINUE) {
            sb.append(", else ").append(elseAction);
            if (elseAction == ConditionFailAction.GOTO_STEP && elseGotoTarget != null && !elseGotoTarget.isEmpty()) {
                sb.append(" (").append(elseGotoTarget).append(")");
            }
        }
        return sb.toString();
    }

    public ConditionType getType() { return type; }
    public void setType(ConditionType type) { this.type = type; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; this.compiledPattern = null; }
    public MatchMode getMatchMode() { return matchMode; }
    public void setMatchMode(MatchMode matchMode) { this.matchMode = matchMode; }
    public ConditionFailAction getAction() { return action; }
    public void setAction(ConditionFailAction action) { this.action = action; }
    public String getGotoTarget() { return gotoTarget; }
    public void setGotoTarget(String gotoTarget) { this.gotoTarget = gotoTarget; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    public ConditionFailAction getElseAction() { return elseAction != null ? elseAction : ConditionFailAction.CONTINUE; }
    public void setElseAction(ConditionFailAction elseAction) { this.elseAction = elseAction; }
    public String getElseGotoTarget() { return elseGotoTarget != null ? elseGotoTarget : ""; }
    public void setElseGotoTarget(String elseGotoTarget) { this.elseGotoTarget = elseGotoTarget; }
}

