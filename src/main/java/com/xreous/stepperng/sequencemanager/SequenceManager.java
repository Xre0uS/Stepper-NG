package com.xreous.stepperng.sequencemanager;

import com.xreous.stepperng.MessageProcessor;
import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequencemanager.listener.StepSequenceListener;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.variable.StepVariable;

import java.util.*;
import java.util.regex.Matcher;

public class SequenceManager {

    private final List<StepSequence> sequences;
    private final List<StepSequenceListener> sequenceListeners;

    public SequenceManager(){
        this.sequences = new ArrayList<>();
        this.sequenceListeners = new ArrayList<>();
    }

    public void addStepSequence(StepSequence sequence){
        this.sequences.add(sequence);
        for (StepSequenceListener stepSequenceListener : this.sequenceListeners) {
            try {
                stepSequenceListener.onStepSequenceAdded(sequence);
            }catch (Exception e){
                Stepper.montoya.logging().logToError("Stepper-NG: Error notifying sequence listener: " + e.getMessage());
            }
        }
    }

    public void removeStepSequence(StepSequence sequence){
        this.sequences.remove(sequence);
        for (StepSequenceListener stepSequenceListener : sequenceListeners) {
            try {
                stepSequenceListener.onStepSequenceRemoved(sequence);
            }catch (Exception e){
                Stepper.montoya.logging().logToError("Stepper-NG: Error notifying sequence listener: " + e.getMessage());
            }
        }
    }

    public void addStepSequenceListener(StepSequenceListener listener){
        this.sequenceListeners.add(listener);
    }

    public void removeStepSequenceListener(StepSequenceListener listener){
        this.sequenceListeners.remove(listener);
    }

    public List<StepSequence> getSequences() {
        return this.sequences;
    }

    public HashMap<StepSequence, List<StepVariable>> getRollingVariablesFromAllSequences(){
        try {
            HashMap<StepSequence, List<StepVariable>> allVariables = new HashMap<>();
            for (StepSequence stepSequence : this.sequences) {
                if (stepSequence.isDisabled()) continue;
                allVariables.put(stepSequence, stepSequence.getRollingVariablesForWholeSequence());
            }
            return allVariables;
        }catch (Exception e){
            Stepper.montoya.logging().logToError("Stepper-NG: Failed to collect variables: " + e.getMessage());
        }
        return new HashMap<>();
    }

    public Set<StepSequence> getSequencesToAutoExecute(String request) {
        Matcher m = MessageProcessor.CROSS_SEQ_VAR_PATTERN.matcher(request);
        Set<StepSequence> toExecute = new LinkedHashSet<>();
        while (m.find()) {
            String seqName = m.group(1);
            String varName = m.group(2);
            for (StepSequence seq : this.sequences) {
                if (seq.isDisabled()) continue;
                if (seq.getTitle().equalsIgnoreCase(seqName)) {
                    if (hasPublishedVariable(seq, varName)) {
                        toExecute.add(seq);
                    }
                    break;
                }
            }
        }
        return toExecute;
    }

    private static boolean hasPublishedVariable(StepSequence seq, String varName) {
        for (Step step : seq.getSteps()) {
            for (StepVariable v : step.getVariableManager().getVariables()) {
                if (v.isPublished() && v.getIdentifier().equalsIgnoreCase(varName)) {
                    return true;
                }
            }
        }
        return false;
    }
}

