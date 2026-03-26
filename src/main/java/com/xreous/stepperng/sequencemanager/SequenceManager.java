package com.xreous.stepperng.sequencemanager;

import com.xreous.stepperng.MessageProcessor;
import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequencemanager.listener.StepSequenceListener;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.variable.StepVariable;

import com.xreous.stepperng.variable.RegexVariable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;

public class SequenceManager {

    private final CopyOnWriteArrayList<StepSequence> sequences;
    private final List<StepSequenceListener> sequenceListeners;

    /**
     * Cached result of whether any published regex variable exists across all sequences.
     */
    private volatile int publishedRegexCacheState = 0; // 0 = unknown, 1 = yes, -1 = no

    public SequenceManager(){
        this.sequences = new CopyOnWriteArrayList<>();
        this.sequenceListeners = new ArrayList<>();
    }

    /**
     * Returns true if at least one published {@link RegexVariable} with a valid regex
     * exists in any enabled sequence.  Result is cached and refreshed via
     * {@link #invalidatePublishedRegexCache()}.
     */
    public boolean hasAnyPublishedRegexVariables() {
        int cached = publishedRegexCacheState;
        if (cached != 0) return cached > 0;
        boolean found = false;
        for (StepSequence seq : this.sequences) {
            if (seq.isDisabled()) continue;
            for (Step step : seq.getSteps()) {
                for (StepVariable v : step.getVariableManager().getVariables()) {
                    if (v.isPublished() && v instanceof RegexVariable rv && rv.isValid()) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            if (found) break;
        }
        publishedRegexCacheState = found ? 1 : -1;
        return found;
    }

    public void invalidatePublishedRegexCache() {
        publishedRegexCacheState = 0;
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
        try {
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
        } catch (Exception e) {
            Stepper.montoya.logging().logToError("Stepper-NG: Failed to determine auto-execute sequences: " + e.getMessage());
            return Collections.emptySet();
        }
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
