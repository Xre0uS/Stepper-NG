package com.xreous.stepperng.sequencemanager;

import com.xreous.stepperng.MessageProcessor;
import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.condition.StepCondition;
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
        this.sequenceListeners = new CopyOnWriteArrayList<>();
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

    /**
     * Replaces the sequence's UUID and every contained step's UUID with fresh values, remapping
     * intra-sequence references (validation step ids, condition goto targets) to the new ids.
     * Required on import / duplicate so cloned sequences don't collide with existing ones in the
     * CardLayout (cards are keyed by UUID) or the manager's id-based lookups.
     */
    public static void reseedIds(StepSequence sequence) {
        if (sequence == null) return;
        sequence.setSequenceId(UUID.randomUUID().toString());
        Map<String, String> idMap = new HashMap<>();
        for (Step step : sequence.getSteps()) {
            String fresh = UUID.randomUUID().toString();
            idMap.put(step.getStepId(), fresh);
            step.setStepId(fresh);
        }
        String v = sequence.getValidationStepId();
        if (v != null && idMap.containsKey(v)) sequence.setValidationStepId(idMap.get(v));
        String pv = sequence.getPostValidationStepId();
        if (pv != null && idMap.containsKey(pv)) sequence.setPostValidationStepId(idMap.get(pv));
        for (Step step : sequence.getSteps()) {
            StepCondition c = step.getCondition();
            if (c == null) continue;
            String g = c.getGotoTarget();
            if (g != null && idMap.containsKey(g)) c.setGotoTarget(idMap.get(g));
            String eg = c.getElseGotoTarget();
            if (eg != null && idMap.containsKey(eg)) c.setElseGotoTarget(idMap.get(eg));
        }
    }

    public StepSequence duplicate(StepSequence original) {
        try {
            com.google.gson.Gson gson = Stepper.getGsonProvider().getGson();
            String json = gson.toJson(original, StepSequence.class);
            StepSequence copy = gson.fromJson(json, StepSequence.class);
            reseedIds(copy);
            copy.setTitle(original.getTitle() + " (Copy)");
            addStepSequence(copy);
            return copy;
        } catch (Exception e) {
            Stepper.montoya.logging().logToError("Stepper-NG: Failed to duplicate sequence '"
                    + original.getTitle() + "': " + e.getMessage());
            return null;
        }
    }

    public void sequenceModified(StepSequence sequence) {
        for (StepSequenceListener listener : this.sequenceListeners) {
            try {
                listener.onStepSequenceModified(sequence);
            } catch (Exception e) {
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

    /**
     * Finds a non-disabled sequence by its sequenceId first, then falls back to
     * case-insensitive title match. Logs a warning when more than one sequence
     * shares the same title so the ambiguity is at least visible.
     */
    public Optional<StepSequence> findSequence(String idOrName) {
        if (idOrName == null || idOrName.isEmpty()) return Optional.empty();
        for (StepSequence seq : this.sequences) {
            if (!seq.isDisabled() && idOrName.equals(seq.getSequenceId())) {
                return Optional.of(seq);
            }
        }
        StepSequence first = null;
        int matches = 0;
        for (StepSequence seq : this.sequences) {
            if (!seq.isDisabled() && seq.getTitle().equalsIgnoreCase(idOrName)) {
                if (first == null) first = seq;
                matches++;
            }
        }
        if (matches > 1) {
            try {
                Stepper.montoya.logging().logToError("Stepper-NG: " + matches
                        + " sequences share the title '" + idOrName + "'; using the first one. "
                        + "Rename sequences or reference by id for deterministic behaviour.");
            } catch (Exception ignored) {}
        }
        return Optional.ofNullable(first);
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
