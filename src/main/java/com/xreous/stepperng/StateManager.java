package com.xreous.stepperng;

import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.persistence.PersistedObject;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequencemanager.listener.StepSequenceListener;
import com.xreous.stepperng.sequencemanager.SequenceManager;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.StepVariableManager;
import com.xreous.stepperng.step.listener.StepListener;
import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.StaticGlobalVariable;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.VariableManager;
import com.xreous.stepperng.variable.listener.StepVariableListener;
import com.xreous.stepperng.util.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StateManager implements StepSequenceListener, StepListener, StepVariableListener, ExtensionUnloadingHandler {

    private final SequenceManager sequenceManager;
    private final Preferences preferences;
    private final DynamicGlobalVariableManager dynamicGlobalVariableManager;
    private volatile boolean unloaded = false;
    private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Stepper-NG-SaveDebounce");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingSequenceSaves = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> pendingGlobalVarSave;
    private static final long SAVE_DEBOUNCE_MS = 2000;

    public StateManager(SequenceManager sequenceManager, Preferences preferences, DynamicGlobalVariableManager dynamicVarManager){
        this.sequenceManager = sequenceManager;
        this.preferences = preferences;
        this.dynamicGlobalVariableManager = dynamicVarManager;
        this.sequenceManager.addStepSequenceListener(this);
        if (this.dynamicGlobalVariableManager != null) {
            this.dynamicGlobalVariableManager.addVariableListener(this);
        }
    }

    // ---- sequence persistence (per-id keys via extensionData) -------------------------------

    private PersistedObject extensionData() {
        return Stepper.montoya != null ? Stepper.montoya.persistence().extensionData() : null;
    }

    private boolean canPersist() {
        return !unloaded && Stepper.montoya != null && !Stepper.isDegradedMode();
    }

    private void saveSequence(StepSequence sequence) {
        if (!canPersist() || sequence == null) return;
        PersistedObject data = extensionData();
        if (data == null) return;
        try {
            Gson gson = Stepper.getGsonProvider().getGson();
            data.setString(Globals.EXTENSION_DATA_SEQUENCE_PREFIX + sequence.getSequenceId(),
                    gson.toJson(sequence, StepSequence.class));
            ensureIdListContains(data, sequence.getSequenceId(), gson);
        } catch (Exception e) {
            logErr("Failed to save sequence '" + sequence.getTitle() + "': " + e.getMessage());
        }
    }

    private void saveAllSequences() {
        if (!canPersist()) return;
        PersistedObject data = extensionData();
        if (data == null) return;
        Gson gson = Stepper.getGsonProvider().getGson();
        List<String> ids = new ArrayList<>();
        for (StepSequence seq : sequenceManager.getSequences()) {
            try {
                data.setString(Globals.EXTENSION_DATA_SEQUENCE_PREFIX + seq.getSequenceId(),
                        gson.toJson(seq, StepSequence.class));
                ids.add(seq.getSequenceId());
            } catch (Exception e) {
                logErr("Failed to save sequence '" + seq.getTitle() + "': " + e.getMessage());
            }
        }
        try {
            data.setString(Globals.EXTENSION_DATA_SEQUENCE_IDS, gson.toJson(ids));
        } catch (Exception e) {
            logErr("Failed to save sequence id list: " + e.getMessage());
        }
    }

    private void removeSequenceFromPersistence(String sequenceId) {
        if (!canPersist() || sequenceId == null) return;
        PersistedObject data = extensionData();
        if (data == null) return;
        try {
            data.deleteString(Globals.EXTENSION_DATA_SEQUENCE_PREFIX + sequenceId);
            Gson gson = Stepper.getGsonProvider().getGson();
            List<String> ids = readSequenceIdList(data, gson);
            if (ids.remove(sequenceId)) {
                data.setString(Globals.EXTENSION_DATA_SEQUENCE_IDS, gson.toJson(ids));
            }
        } catch (Exception e) {
            logErr("Failed to remove sequence id " + sequenceId + ": " + e.getMessage());
        }
    }

    private void ensureIdListContains(PersistedObject data, String id, Gson gson) {
        List<String> ids = readSequenceIdList(data, gson);
        if (!ids.contains(id)) {
            ids.add(id);
            try { data.setString(Globals.EXTENSION_DATA_SEQUENCE_IDS, gson.toJson(ids)); }
            catch (Exception e) { logErr("Failed to update sequence id list: " + e.getMessage()); }
        }
    }

    private List<String> readSequenceIdList(PersistedObject data, Gson gson) {
        try {
            String raw = data.getString(Globals.EXTENSION_DATA_SEQUENCE_IDS);
            if (raw == null || raw.isBlank()) return new ArrayList<>();
            List<String> parsed = gson.fromJson(raw, new TypeToken<ArrayList<String>>(){}.getType());
            return parsed != null ? parsed : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void scheduleSaveSequence(StepSequence sequence) {
        if (!canPersist() || sequence == null) return;
        String id = sequence.getSequenceId();
        ScheduledFuture<?> fresh = saveScheduler.schedule(() -> {
            pendingSequenceSaves.remove(id);
            saveSequence(sequence);
        }, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> prev = pendingSequenceSaves.put(id, fresh);
        if (prev != null) prev.cancel(false);
    }

    public void loadSavedSequences() {
        PersistedObject data = extensionData();
        if (data == null) return;
        Gson gson = Stepper.getGsonProvider().getGson();

        List<String> ids = readSequenceIdList(data, gson);
        if (!ids.isEmpty()) {
            Set<String> seen = new HashSet<>();
            for (String id : ids) {
                if (!seen.add(id)) continue;
                try {
                    String raw = data.getString(Globals.EXTENSION_DATA_SEQUENCE_PREFIX + id);
                    if (raw == null || raw.isBlank()) continue;
                    StepSequence seq = gson.fromJson(raw, StepSequence.class);
                    if (seq != null) sequenceManager.addStepSequence(seq);
                } catch (Exception e) {
                    logErr("Failed to load sequence '" + id + "': " + e.getMessage());
                }
            }
            return;
        }

        // Migration path: read legacy monolithic key and persist under per-id keys.
        if (this.preferences == null) return;
        try {
            ArrayList<StepSequence> legacy = this.preferences.getSetting(Globals.PREF_STEP_SEQUENCES);
            if (legacy == null || legacy.isEmpty()) return;
            for (StepSequence seq : legacy) sequenceManager.addStepSequence(seq);
            if (canPersist()) saveAllSequences();
        } catch (Exception e) {
            logErr("Failed to migrate legacy sequences: " + e.getMessage());
        }
    }

    // ---- global-variable persistence (still monolithic — small, always fully re-read) ---------

    public void saveDynamicGlobalVars() {
        if (unloaded || this.preferences == null) return;
        try {
            this.preferences.setSetting(Globals.PREF_DYNAMIC_GLOBAL_VARS,
                    new ArrayList<>(this.dynamicGlobalVariableManager.getVariables()));
        } catch (Exception e) {
            logErr("Failed to save DVARs: " + e.getMessage());
        }
        try {
            this.preferences.setSetting(Globals.PREF_STATIC_GLOBAL_VARS,
                    new ArrayList<>(this.dynamicGlobalVariableManager.getStaticVariables()));
        } catch (Exception e) {
            logErr("Failed to save GVARs: " + e.getMessage());
        }
    }

    private void scheduleSaveGlobalVars() {
        if (unloaded || this.preferences == null) return;
        ScheduledFuture<?> prev = pendingGlobalVarSave;
        if (prev != null) prev.cancel(false);
        pendingGlobalVarSave = saveScheduler.schedule(this::saveDynamicGlobalVars, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    public void loadDynamicGlobalVars() {
        if (this.preferences == null) return;
        try {
            ArrayList<DynamicGlobalVariable> vars = this.preferences.getSetting(Globals.PREF_DYNAMIC_GLOBAL_VARS);
            if (vars != null) for (DynamicGlobalVariable v : vars) this.dynamicGlobalVariableManager.addVariable(v);
        } catch (Exception e) {
            logErr("Failed to load DVARs: " + e.getMessage());
        }
        try {
            ArrayList<StaticGlobalVariable> svars = this.preferences.getSetting(Globals.PREF_STATIC_GLOBAL_VARS);
            if (svars != null) for (StaticGlobalVariable v : svars) this.dynamicGlobalVariableManager.addStaticVariable(v);
        } catch (Exception e) {
            logErr("Failed to load GVARs: " + e.getMessage());
        }
    }

    // ---- listener callbacks --------------------------------------------------------------------

    @Override
    public void onStepSequenceAdded(StepSequence sequence) {
        sequence.addStepListener(this);
        sequenceManager.invalidatePublishedRegexCache();
        saveSequence(sequence);
    }

    @Override
    public void onStepSequenceRemoved(StepSequence sequence) {
        sequence.removeStepListener(this);
        sequenceManager.invalidatePublishedRegexCache();
        ScheduledFuture<?> pending = pendingSequenceSaves.remove(sequence.getSequenceId());
        if (pending != null) pending.cancel(false);
        removeSequenceFromPersistence(sequence.getSequenceId());
    }

    @Override
    public void onStepSequenceModified(StepSequence sequence) {
        scheduleSaveSequence(sequence);
    }

    @Override public void onStepUpdated(Step step) { scheduleSaveSequence(step.getSequence()); }

    @Override
    public void onStepAdded(Step step) {
        sequenceManager.invalidatePublishedRegexCache();
        step.getVariableManager().addVariableListener(this);
        saveSequence(step.getSequence());
    }

    @Override
    public void onStepRemoved(Step step) {
        step.getVariableManager().removeVariableListener(this);
        sequenceManager.invalidatePublishedRegexCache();
        saveSequence(step.getSequence());
    }

    @Override public void onVariableAdded(StepVariable variable)   { onVariableTouched(variable, false); }
    @Override public void onVariableRemoved(StepVariable variable) { onVariableTouched(variable, true); }
    @Override public void onVariableChange(StepVariable variable)  { onVariableTouched(variable, false); }

    private void onVariableTouched(StepVariable variable, boolean immediate) {
        sequenceManager.invalidatePublishedRegexCache();
        VariableManager vm = variable != null ? variable.getVariableManager() : null;
        if (vm instanceof StepVariableManager svm) {
            StepSequence seq = svm.getStep() != null ? svm.getStep().getSequence() : null;
            if (seq != null) {
                if (immediate) saveSequence(seq);
                else scheduleSaveSequence(seq);
            }
        } else {
            // DVAR / GVAR managers — global-variable store handles both.
            if (immediate) saveDynamicGlobalVars();
            else scheduleSaveGlobalVars();
        }
    }

    @Override
    public void extensionUnloaded() {
        try {
            for (ScheduledFuture<?> f : pendingSequenceSaves.values()) f.cancel(false);
            pendingSequenceSaves.clear();
            ScheduledFuture<?> pg = pendingGlobalVarSave;
            if (pg != null) pg.cancel(false);
            saveAllSequences();
            saveDynamicGlobalVars();
        } catch (Exception e) {
            logErr("Error during unload save: " + e.getMessage());
        }

        try {
            AutoBackupManager backup = Stepper.getInstance() != null ? Stepper.getInstance().getAutoBackupManager() : null;
            if (backup != null) {
                if (backup.isEnabled()) backup.performBackup();
                backup.shutdown();
            }
        } catch (Exception e) {
            logErr("Error during unload backup: " + e.getMessage());
        }

        unloaded = true;
        saveScheduler.shutdownNow();
        MessageProcessor.cleanup();
        Utils.clearCaches();
        Stepper.cleanup();
    }

    private static void logErr(String msg) {
        try { Stepper.montoya.logging().logToError("Stepper-NG: " + msg); } catch (Exception ignored) {}
    }
}
