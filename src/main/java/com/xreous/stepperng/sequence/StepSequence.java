package com.xreous.stepperng.sequence;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.xreous.stepperng.Globals;
import com.xreous.stepperng.MessageProcessor;
import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.condition.ConditionFailAction;
import com.xreous.stepperng.condition.StepCondition;
import com.xreous.stepperng.exception.SequenceCancelledException;
import com.xreous.stepperng.exception.SequenceExecutionException;
import com.xreous.stepperng.sequence.listener.SequenceExecutionListener;
import com.xreous.stepperng.sequence.view.SequenceContainer;
import com.xreous.stepperng.sequence.view.StepSequenceTab;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.StepExecutionInfo;
import com.xreous.stepperng.variable.PreExecutionStepVariable;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.VariableManager;
import com.xreous.stepperng.step.listener.StepListener;
import com.xreous.stepperng.step.view.StepPanel;

import java.util.*;

public class StepSequence
{
    private String title;
    private boolean isExecuting;
    private boolean disabled;
    private VariableManager globalVariablesManager;
    private Vector<Step> steps;
    private Integer validationStepIndex;
    private final ArrayList<StepListener> stepListeners;
    private final ArrayList<SequenceExecutionListener> sequenceExecutionListeners;

    public StepSequence(String title){
        this.steps = new Vector<>();
        this.stepListeners = new ArrayList<>();
        this.globalVariablesManager = new GlobalVariableManager(this);
        this.sequenceExecutionListeners = new ArrayList<>();
        this.title = title;
    }

    public StepSequence(){
        this("Step Sequence");
    }

    public void executeBlocking(){
        executeBlocking(new HashMap<>());
    }

    public void executeBlocking(Map<String, String> arguments){
        if(this.isExecuting) return;
        if(this.disabled) return;

        if (MessageProcessor.isSequenceOnStack(this.title)) {
            Stepper.montoya.logging().logToError("Stepper-NG: Skipping sequence '" + this.title + "' - already on execution stack (cycle detected).");
            return;
        }
        if (MessageProcessor.getStackDepth() >= Globals.MAX_SEQUENCE_DEPTH) {
            Stepper.montoya.logging().logToError("Stepper-NG: Skipping sequence '" + this.title + "' - max depth (" + Globals.MAX_SEQUENCE_DEPTH + ") reached.");
            return;
        }

        this.isExecuting = true;
        MessageProcessor.pushSequence(this.title);
        try {
            synchronized (StepSequence.this) {
                Map<StepVariable, String> savedValues = new HashMap<>();
                if (arguments != null && !arguments.isEmpty()) {
                    for (StepVariable variable : this.globalVariablesManager.getVariables()) {
                        if (arguments.containsKey(variable.getIdentifier())) {
                            savedValues.put(variable, variable.getValue());
                            variable.setValue(arguments.get(variable.getIdentifier()));
                        }
                    }
                }

                SequenceContainer sequenceContainer = null;
                try {
                    StepSequenceTab tabUI = Stepper.getUI().getTabForStepManager(this);
                    sequenceContainer = tabUI.getStepsContainer();
                    sequenceContainer.beginExecution();

                    if (!Stepper.getUI().isSequenceVisible(this)) {
                        if (!Stepper.getUI().isStepperTabVisible()) {
                            Stepper.getUI().highlightTab();
                        }
                        Stepper.getUI().highlightSequenceTab(this);
                    }

                    for (SequenceExecutionListener stepListener : this.sequenceExecutionListeners) {
                        stepListener.beforeSequenceStart(this.steps);
                    }

                    // Validation step
                    if (validationStepIndex != null && validationStepIndex >= 0 && validationStepIndex < steps.size()) {
                        Step valStep = steps.get(validationStepIndex);
                        StepCondition valCondition = valStep.getCondition();
                        boolean hasUsableCondition = valCondition != null && valCondition.isConfigured()
                                && valCondition.getType() != StepCondition.ConditionType.ALWAYS;
                        if (hasUsableCondition && valStep.isEnabled() && valStep.isReadyToExecute()) {
                            StepPanel valPanel = sequenceContainer.getPanelForStep(valStep);
                            byte[] valRequest = valPanel.getRequestEditor().getMessage();
                            valStep.setRequestBody(valRequest);

                            boolean hasUnresolvedVars = false;
                            if (MessageProcessor.hasStepVariable(valRequest)) {
                                List<StepVariable> allVars = this.getRollingVariablesForWholeSequence();
                                hasUnresolvedVars = allVars.stream()
                                        .anyMatch(v -> v.getValue() == null || v.getValue().isEmpty());
                            }

                            if (hasUnresolvedVars) {
                                valStep.setLastConditionResult("Skipped — variables not yet populated");
                                stepModified(valStep);
                            } else {
                                sequenceContainer.setActivePanel(valPanel);
                                try {
                                    List<StepVariable> valReplacements = this.getRollingVariablesUpToStep(valStep);
                                    StepExecutionInfo valResult = valStep.executeStep(valReplacements);
                                    this.sequenceExecutionListeners.forEach(l -> l.sequenceStepExecuted(valResult));
                                    valStep.setLastExecutionTime(System.currentTimeMillis());
                                    if (valCondition.evaluate(valResult)) {
                                        valStep.setLastConditionResult("Triggered → session valid, skipped rest");
                                        stepModified(valStep);
                                        for (SequenceExecutionListener l : sequenceExecutionListeners) l.afterSequenceEnd(true);
                                        return;
                                    } else {
                                        valStep.setLastConditionResult("Not triggered → session invalid, running full sequence");
                                        stepModified(valStep);
                                    }
                                } catch (Exception e) {
                                    valStep.setLastConditionResult("Error — running full sequence");
                                    stepModified(valStep);
                                }
                            }
                        }
                    }

                    for (Step step : this.steps) {
                        if (!step.isEnabled()) continue;
                        StepPanel panel = sequenceContainer.getPanelForStep(step);
                        step.setRequestBody(panel.getRequestEditor().getMessage());
                        if (!step.isReadyToExecute()) {
                            Stepper.montoya.logging().logToError("Stepper-NG: Sequence '" + title + "' — step '" + step.getTitle() + "' is not ready to execute.");
                            for (SequenceExecutionListener l : this.sequenceExecutionListeners) l.afterSequenceEnd(false);
                            return;
                        }
                    }

                    boolean sequenceSuccess = false;
                    int maxIterations = steps.size() * 10;
                    int iterations = 0;
                    try {
                        int stepIndex = 0;
                        while (stepIndex < steps.size() && iterations < maxIterations) {
                            Step step = steps.get(stepIndex);
                            iterations++;

                            if (!step.isEnabled()) { stepIndex++; continue; }

                            // Skip validation step — it was already executed in the pre-validation phase
                            if (validationStepIndex != null && stepIndex == validationStepIndex) {
                                stepIndex++;
                                continue;
                            }

                            StepPanel panel = sequenceContainer.getPanelForStep(step);
                            sequenceContainer.setActivePanel(panel);
                            List<StepVariable> rollingReplacements = this.getRollingVariablesUpToStep(step);

                            StepCondition cond = step.getCondition();
                            // "Always" conditions trigger on the first attempt — retries are meaningless
                            boolean isAlways = cond != null && cond.getType() == StepCondition.ConditionType.ALWAYS;
                            int maxRetries = (cond != null && !isAlways) ? cond.getRetryCount() : 0;
                            StepExecutionInfo stepResult = null;
                            boolean conditionTriggered = false;

                            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                                if (attempt > 0 && cond != null && cond.getRetryDelayMs() > 0) {
                                    try { Thread.sleep(cond.getRetryDelayMs()); } catch (InterruptedException ignored) {}
                                }
                                stepResult = step.executeStep(rollingReplacements);
                                final StepExecutionInfo result = stepResult;
                                this.sequenceExecutionListeners.forEach(l -> l.sequenceStepExecuted(result));

                                if (cond != null && cond.isConfigured()) {
                                    conditionTriggered = cond.evaluate(stepResult);
                                    if (!conditionTriggered) break;
                                } else {
                                    break;
                                }
                            }

                            step.setLastExecutionTime(System.currentTimeMillis());

                            if (conditionTriggered && cond != null) {
                                switch (cond.getAction()) {
                                    case SKIP_REMAINING -> {
                                        step.setLastConditionResult("Triggered → skipped remaining steps");
                                        stepModified(step);
                                        stepIndex = steps.size();
                                        continue;
                                    }
                                    case GOTO_STEP -> {
                                        int target = resolveStepIndex(cond.getGotoTarget());
                                        step.setLastConditionResult("Triggered → goto " + cond.getGotoTarget());
                                        stepModified(step);
                                        if (target >= 0) { stepIndex = target; continue; }
                                        stepIndex++;
                                        continue;
                                    }
                                    case CONTINUE -> step.setLastConditionResult("Triggered → continued");
                                }
                            } else if (cond != null && cond.isConfigured()) {
                                ConditionFailAction elseAct = cond.getElseAction();
                                switch (elseAct) {
                                    case SKIP_REMAINING -> {
                                        step.setLastConditionResult("Not triggered → skipped remaining steps");
                                        stepModified(step);
                                        stepIndex = steps.size();
                                        continue;
                                    }
                                    case GOTO_STEP -> {
                                        int target = resolveStepIndex(cond.getElseGotoTarget());
                                        step.setLastConditionResult("Not triggered → goto " + cond.getElseGotoTarget());
                                        stepModified(step);
                                        if (target >= 0) { stepIndex = target; continue; }
                                        stepIndex++;
                                        continue;
                                    }
                                    case CONTINUE -> step.setLastConditionResult("Not triggered → continued");
                                }
                            } else {
                                step.setLastConditionResult("Executed");
                            }
                            stepModified(step);

                            stepIndex++;
                        }
                        sequenceSuccess = true;
                    } catch (SequenceCancelledException e) {
                    } catch (SequenceExecutionException e) {
                        try { Stepper.montoya.logging().logToError("Stepper-NG: Sequence '" + title + "' stopped: " + e.getMessage()); } catch (Exception ignored) {}
                    } catch (Exception e) {
                        try { Stepper.montoya.logging().logToError("Stepper-NG: Sequence '" + title + "' failed: " + e.getMessage()); } catch (Exception ignored) {}
                    }
                    for (SequenceExecutionListener listener : sequenceExecutionListeners) {
                        listener.afterSequenceEnd(sequenceSuccess);
                    }
                } finally {
                    for (Map.Entry<StepVariable, String> entry : savedValues.entrySet()) {
                        entry.getKey().setValue(entry.getValue());
                    }
                    if (sequenceContainer != null) {
                        try { sequenceContainer.endExecution(); } catch (Exception ignored) {}
                    }
                }
            }
        } finally {
            MessageProcessor.popSequence();
            this.isExecuting = false;
        }
    }

    private int resolveStepIndex(String stepTitle) {
        if (stepTitle == null || stepTitle.isEmpty()) return -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getTitle().equalsIgnoreCase(stepTitle.trim())) return i;
        }
        try { return Integer.parseInt(stepTitle.trim()) - 1; } catch (NumberFormatException e) { return -1; }
    }

    public void executeAsync(){
        new Thread(this::executeBlocking).start();
    }

    public void addStep(Step step){
        this.steps.add(step);
        step.setSequence(this);
        for (StepListener stepListener : this.stepListeners) {
            try {
                stepListener.onStepAdded(step);
            }catch (Exception e){
                Stepper.montoya.logging().logToError("Stepper-NG: Error notifying step listener: " + e.getMessage());
            }
        }
        if (Stepper.getUI() != null && !Stepper.getUI().isStepperTabVisible()) {
            Stepper.getUI().highlightTab();
        }
    }

    public void addStep(){
        this.addStep(new Step(this));
    }

    public void addStep(HttpRequestResponse requestResponse) {
        Step step = new Step(this);
        if(requestResponse.request() != null) {
            step.setRequestBody(requestResponse.request().toByteArray().getBytes());
        }
        if(requestResponse.response() != null) {
            step.setResponseBody(requestResponse.response().toByteArray().getBytes());
        }
        if(requestResponse.httpService() != null) {
            step.setHttpService(requestResponse.httpService());
        }
        addStep(step);
    }

    public void stepModified(Step step){
        for (StepListener stepListener : this.stepListeners) {
            stepListener.onStepUpdated(step);
        }
    }

    public void removeStep(Step step) {
        if(!this.steps.remove(step)) throw new IllegalArgumentException("Step not valid for sequence");
        for (StepListener stepListener : this.stepListeners) {
            stepListener.onStepRemoved(step);
        }
    }

    public Vector<Step> getSteps() {
        return this.steps;
    }

    public boolean isExecuting() { return this.isExecuting; }

    public void moveStep(int from, int to){
        if (from == to) return;


        Step movedStep = this.steps.remove(from);
        this.steps.add(to, movedStep);

        if (validationStepIndex != null) {
            if (validationStepIndex == from) {
                validationStepIndex = to;
            } else {
                int vi = validationStepIndex;
                if (from < vi && to >= vi) vi--;
                else if (from > vi && to <= vi) vi++;
                validationStepIndex = vi;
            }
        }

        for (Step step : steps) {
            StepCondition cond = step.getCondition();
            if (cond == null || cond.getAction() != ConditionFailAction.GOTO_STEP) continue;
            String target = cond.getGotoTarget();
            if (target == null || target.isEmpty()) continue;
            try {
                int oldIdx = Integer.parseInt(target.trim()) - 1;
                int newIdx = resolveNewIndex(oldIdx, from, to);
                if (newIdx != oldIdx) {
                    cond.setGotoTarget(String.valueOf(newIdx + 1));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        for (StepListener stepListener : this.stepListeners) {
            stepListener.onStepUpdated(movedStep);
        }
    }

    private int resolveNewIndex(int oldIdx, int movedFrom, int movedTo) {
        if (oldIdx == movedFrom) return movedTo;
        int idx = oldIdx;
        if (movedFrom < idx && movedTo >= idx) idx--;
        else if (movedFrom > idx && movedTo <= idx) idx++;
        return idx;
    }

    public void addSequenceExecutionListener(SequenceExecutionListener listener){
        this.sequenceExecutionListeners.add(listener);
    }

    public void removeSequenceExecutionListener(SequenceExecutionListener listener){
        this.sequenceExecutionListeners.remove(listener);
    }

    public void addStepListener(StepListener listener){
        this.stepListeners.add(listener);
    }

    public void removeStepListener(StepListener listener){
        this.stepListeners.remove(listener);
    }

    public void syncVariableValues(Step sourceStep) {
        for (StepVariable sourceVar : sourceStep.getVariableManager().getVariables()) {
            String val = sourceVar.getValue();
            if (val == null || val.isEmpty()) continue;
            String id = sourceVar.getIdentifier();
            for (Step otherStep : this.steps) {
                if (otherStep == sourceStep) continue;
                for (StepVariable otherVar : otherStep.getVariableManager().getVariables()) {
                    if (otherVar.getIdentifier().equals(id) && !val.equals(otherVar.getValue())) {
                        otherVar.setValue(val);
                    }
                }
            }
        }
    }

    public List<StepVariable> getRollingVariablesForWholeSequence() {
        return getRollingVariablesUpToStep(null);
    }

    public List<StepVariable> getRollingVariablesUpToStep(Step uptoStep){
        LinkedHashMap<String, StepVariable> rolling = new LinkedHashMap<>();

        for (Step step : this.steps) {
            if(uptoStep == step){
                if (step.isEnabled()) {
                    for (PreExecutionStepVariable preExecutionVariable : step.getVariableManager().getPreExecutionVariables()) {
                        rolling.put(preExecutionVariable.getIdentifier(), preExecutionVariable);
                    }
                }
                break;
            }
            if (!step.isEnabled()) continue;
            for (StepVariable variable : step.getVariableManager().getVariables()) {
                rolling.put(variable.getIdentifier(), variable);
            }
        }

        return new ArrayList<>(rolling.values());
    }

    public ArrayList<StepListener> getStepListeners() {
        return stepListeners;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public VariableManager getGlobalVariableManager() {
        return this.globalVariablesManager;
    }

    public Integer getValidationStepIndex() {
        return validationStepIndex;
    }

    public void setValidationStepIndex(Integer validationStepIndex) {
        this.validationStepIndex = validationStepIndex;
    }
}
