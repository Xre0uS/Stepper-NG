package com.xreous.stepperng;

import burp.api.montoya.extension.ExtensionUnloadingHandler;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequencemanager.listener.StepSequenceListener;
import com.xreous.stepperng.sequencemanager.SequenceManager;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.listener.StepListener;
import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.StaticGlobalVariable;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.listener.StepVariableListener;
import com.xreous.stepperng.util.Utils;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StateManager implements StepSequenceListener, StepListener, StepVariableListener, ExtensionUnloadingHandler {

    private SequenceManager sequenceManager;
    private Preferences preferences;
    private DynamicGlobalVariableManager dynamicGlobalVariableManager;
    private volatile boolean unloaded = false;
    private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Stepper-NG-SaveDebounce");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> pendingSequenceSave;
    private volatile ScheduledFuture<?> pendingGlobalVarSave;
    private static final long SAVE_DEBOUNCE_MS = 500;

    public StateManager(SequenceManager sequenceManager, Preferences preferences, DynamicGlobalVariableManager dynamicVarManager){
        this.sequenceManager = sequenceManager;
        this.preferences = preferences;
        this.dynamicGlobalVariableManager = dynamicVarManager;
        this.sequenceManager.addStepSequenceListener(this);
        if (this.dynamicGlobalVariableManager != null) {
            this.dynamicGlobalVariableManager.addVariableListener(this);
        }
    }

    public void saveCurrentSequences(){
        if (unloaded) return;
        try {
            this.preferences.setSetting(Globals.PREF_STEP_SEQUENCES, this.sequenceManager.getSequences());
        } catch (Exception ignored) {}
    }

    public void saveDynamicGlobalVars(){
        if (unloaded) return;
        try {
            this.preferences.setSetting(Globals.PREF_DYNAMIC_GLOBAL_VARS,
                    new ArrayList<>(this.dynamicGlobalVariableManager.getVariables()));
        } catch (Exception ignored) { }
        try {
            this.preferences.setSetting(Globals.PREF_STATIC_GLOBAL_VARS,
                    new ArrayList<>(this.dynamicGlobalVariableManager.getStaticVariables()));
        } catch (Exception ignored) { }
    }

    private void scheduleSaveSequences() {
        if (unloaded) return;
        ScheduledFuture<?> prev = pendingSequenceSave;
        if (prev != null) prev.cancel(false);
        pendingSequenceSave = saveScheduler.schedule(this::saveCurrentSequences, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void scheduleSaveGlobalVars() {
        if (unloaded) return;
        ScheduledFuture<?> prev = pendingGlobalVarSave;
        if (prev != null) prev.cancel(false);
        pendingGlobalVarSave = saveScheduler.schedule(this::saveDynamicGlobalVars, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    public void loadSavedSequences(){
        ArrayList<StepSequence> stepSequences = this.preferences.getSetting(Globals.PREF_STEP_SEQUENCES);
        if(stepSequences != null) {
            for (StepSequence stepSequence : stepSequences) {
                this.sequenceManager.addStepSequence(stepSequence);
            }
        }
    }

    public void loadDynamicGlobalVars(){
        try {
            ArrayList<DynamicGlobalVariable> vars = this.preferences.getSetting(Globals.PREF_DYNAMIC_GLOBAL_VARS);
            if (vars != null) {
                for (DynamicGlobalVariable var : vars) {
                    this.dynamicGlobalVariableManager.addVariable(var);
                }
            }
        } catch (Exception e) { }
        try {
            ArrayList<StaticGlobalVariable> svars = this.preferences.getSetting(Globals.PREF_STATIC_GLOBAL_VARS);
            if (svars != null) {
                for (StaticGlobalVariable svar : svars) {
                    this.dynamicGlobalVariableManager.addStaticVariable(svar);
                }
            }
        } catch (Exception e) { }
    }

    @Override
    public void onStepSequenceAdded(StepSequence sequence) {
        sequence.addStepListener(this);
        sequence.getGlobalVariableManager().addVariableListener(this);
        saveCurrentSequences();
    }

    @Override public void onStepUpdated(Step step) { scheduleSaveSequences(); }

    @Override
    public void onStepSequenceRemoved(StepSequence sequence) {
        sequence.removeStepListener(this);
        sequence.getGlobalVariableManager().removeVariableListener(this);
        saveCurrentSequences();
    }

    @Override
    public void onStepAdded(Step step) {
        saveCurrentSequences();
        step.getVariableManager().addVariableListener(this);
    }

    @Override
    public void onStepRemoved(Step step) {
        step.getVariableManager().removeVariableListener(this);
        saveCurrentSequences();
    }

    @Override public void onVariableAdded(StepVariable variable) { scheduleSaveSequences(); scheduleSaveGlobalVars(); }
    @Override public void onVariableRemoved(StepVariable variable) { saveCurrentSequences(); saveDynamicGlobalVars(); }
    @Override public void onVariableChange(StepVariable variable) { scheduleSaveSequences(); scheduleSaveGlobalVars(); }

    @Override
    public void extensionUnloaded() {
        try {
            // Cancel any pending debounced saves
            ScheduledFuture<?> ps = pendingSequenceSave;
            if (ps != null) ps.cancel(false);
            ScheduledFuture<?> pg = pendingGlobalVarSave;
            if (pg != null) pg.cancel(false);
            // Final immediate save
            saveCurrentSequences();
            saveDynamicGlobalVars();
        } catch (Exception ignored) {}
        unloaded = true;
        saveScheduler.shutdownNow();
        Utils.clearCaches();
    }
}
