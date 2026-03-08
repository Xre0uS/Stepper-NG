package com.xreous.stepperng.variable;

import com.xreous.stepperng.step.StepExecutionInfo;
import com.xreous.stepperng.step.StepVariableManager;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegexVariable extends PostExecutionStepVariable {

    transient Pattern regex = null;
    String regexString = null;

    public RegexVariable(){
        this(UUID.randomUUID().toString(), null);
    }

    public RegexVariable(String identifier){
        this(identifier, null);
    }

    public RegexVariable(String identifier, String regex){
        super(identifier);
        this.regexString = regex;
        try {
            if(regex != null) {
                this.regex = Pattern.compile(regex, Pattern.DOTALL);
            }
        } catch (PatternSyntaxException ignored) {
        }
    }

    private Pattern ensureCompiled() {
        if (this.regex == null && this.regexString != null && !this.regexString.isEmpty()) {
            try {
                this.regex = Pattern.compile(this.regexString, Pattern.DOTALL);
            } catch (PatternSyntaxException ignored) {}
        }
        return this.regex;
    }

    @Override
    public void setCondition(String regex) {
        this.regexString = regex;
        try{
            this.regex = Pattern.compile(regex, Pattern.DOTALL);
        }catch (PatternSyntaxException e){
            this.regex = null;
        }
        if(this.variableManager != null) {
            ((StepVariableManager) this.variableManager).updateVariableWithPreviousExecutionResult(this);
        }
        notifyChanges();
    }

    @Override
    public String getConditionText() {
        return this.regexString;
    }

    @Override
    public String getValuePreview() {
        return this.value;
    }

    @Override
    public boolean isValid() {
        return ensureCompiled() != null;
    }

    @Override
    public void updateVariableAfterExecution(StepExecutionInfo executionInfo) {
        if(executionInfo == null)
            return;

        String response = new String(executionInfo.getRequestResponse().response().toByteArray().getBytes());
        applyRegex(response);
    }

    public void updateFromResponseBytes(byte[] responseBytes) {
        if (responseBytes == null || responseBytes.length == 0 || ensureCompiled() == null) return;
        applyRegex(new String(responseBytes));
    }

    public void updateFromResponse(String responseText) {
        if (responseText == null || responseText.isEmpty() || ensureCompiled() == null) return;
        applyRegex(responseText);
    }

    private void applyRegex(String response) {
        Pattern p = ensureCompiled();
        if (p == null) return;
        Matcher m = p.matcher(response);
        if(m.find()) {
            if(m.groupCount() > 0) this.value = m.group(1);
            else this.value = m.group();
            this.lastUpdated = System.currentTimeMillis();
            notifyChanges();
        }
    }



    public Pattern getPattern() {
        return ensureCompiled();
    }

    @Override
    public String getType() {
        return "Regex";
    }
}
