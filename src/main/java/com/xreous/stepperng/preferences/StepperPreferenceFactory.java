package com.xreous.stepperng.preferences;

import burp.api.montoya.MontoyaApi;
import com.coreyd97.BurpExtenderUtilities.IGsonProvider;
import com.coreyd97.BurpExtenderUtilities.ILogProvider;
import com.coreyd97.BurpExtenderUtilities.PreferenceFactory;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.xreous.stepperng.Globals;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequence.serializer.StepSequenceSerializer;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.serializer.StepSerializer;
import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.PromptVariable;
import com.xreous.stepperng.variable.RegexVariable;
import com.xreous.stepperng.variable.StaticGlobalVariable;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.serializer.DynamicGlobalVariableSerializer;
import com.xreous.stepperng.variable.serializer.PromptVariableSerializer;
import com.xreous.stepperng.variable.serializer.RegexVariableSerializer;
import com.xreous.stepperng.variable.serializer.StaticGlobalVariableSerializer;
import com.xreous.stepperng.variable.serializer.VariableSerializer;
import com.coreyd97.BurpExtenderUtilities.nameManager.NameManager;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class StepperPreferenceFactory extends PreferenceFactory {

    /**
     * Set to true if any PROJECT-scoped setting failed to load from Burp's persistence
     * (e.g. corrupted project file). When true, the extension runs in degraded mode
     * with in-memory-only storage for those settings.
     */
    private boolean projectSettingsCorrupted = false;

    public StepperPreferenceFactory(String extensionIdentifier, IGsonProvider gsonProvider, MontoyaApi montoyaApi) {
        super(montoyaApi, gsonProvider, new ILogProvider() {
            @Override
            public void logOutput(String message) {
                montoyaApi.logging().logToOutput(message);
            }

            @Override
            public void logError(String errorMessage) {
                montoyaApi.logging().logToError(errorMessage);
            }
        });
    }

    public boolean isProjectSettingsCorrupted() {
        return projectSettingsCorrupted;
    }

    @Override
    protected void createDefaults() {
    }

    @Override
    protected void registerTypeAdapters() {
        gsonProvider.registerTypeAdapter(new TypeToken<StepSequence>(){}.getType(), new StepSequenceSerializer());
        gsonProvider.registerTypeAdapter(new TypeToken<Step>(){}.getType(), new StepSerializer());
        gsonProvider.registerTypeAdapter(new TypeToken<StepVariable>(){}.getType(), new VariableSerializer());
        gsonProvider.registerTypeAdapter(new TypeToken<PromptVariable>(){}.getType(), new PromptVariableSerializer());
        gsonProvider.registerTypeAdapter(new TypeToken<RegexVariable>(){}.getType(), new RegexVariableSerializer());
        gsonProvider.registerTypeAdapter(new TypeToken<DynamicGlobalVariable>(){}.getType(), new DynamicGlobalVariableSerializer());
        gsonProvider.registerTypeAdapter(new TypeToken<StaticGlobalVariable>(){}.getType(), new StaticGlobalVariableSerializer());
    }

    private void registerProjectSettingSafe(String name, Type type) {
        try {
            prefs.registerSetting(name, type, Preferences.Visibility.PROJECT);
        } catch (Exception e) {
            projectSettingsCorrupted = true;
            // Partial registration leaves NameManager with a reservation — release it
            try { NameManager.release(name); } catch (Exception ignored) {}
            prefs.registerSetting(name, type, Preferences.Visibility.VOLATILE);
            if (logProvider != null) {
                logProvider.logError("Stepper-NG: Project setting '" + name
                        + "' could not be read (corrupted project file?). Using in-memory fallback.");
            }
        }
    }

    @Override
    protected void registerSettings() {
        registerProjectSettingSafe(Globals.PREF_STEP_SEQUENCES, new TypeToken<ArrayList<StepSequence>>(){}.getType());
        if (projectSettingsCorrupted) {
            // First project setting already failed at the persistence-API level,
            // meaning the whole layer is broken.  Register the remaining project
            // settings directly as VOLATILE to avoid more noisy error output.
            prefs.registerSetting(Globals.PREF_DYNAMIC_GLOBAL_VARS,
                    new TypeToken<ArrayList<DynamicGlobalVariable>>(){}.getType(), Preferences.Visibility.VOLATILE);
            prefs.registerSetting(Globals.PREF_STATIC_GLOBAL_VARS,
                    new TypeToken<ArrayList<StaticGlobalVariable>>(){}.getType(), Preferences.Visibility.VOLATILE);
        } else {
            registerProjectSettingSafe(Globals.PREF_DYNAMIC_GLOBAL_VARS, new TypeToken<ArrayList<DynamicGlobalVariable>>(){}.getType());
            registerProjectSettingSafe(Globals.PREF_STATIC_GLOBAL_VARS, new TypeToken<ArrayList<StaticGlobalVariable>>(){}.getType());
        }
        prefs.registerSetting(Globals.PREF_PREV_VERSION, String.class, Globals.VERSION, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_ALL_TOOLS, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_EXTENDER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_SEQUENCER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_REPEATER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_PROXY, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_INTRUDER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_SCANNER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_UPDATE_REQUEST_LENGTH, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_SESSION_VALIDATE_EVERY_N, Integer.class, 1, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_PAUSE_ON_POST_VALIDATION_FAIL, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_HOLD_REQUESTS_DURING_EXECUTION, Boolean.class, false, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_AUTO_BACKUP_ENABLED, Boolean.class, false, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_AUTO_BACKUP_INTERVAL_MINUTES, Integer.class, Globals.DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_AUTO_BACKUP_DIR, String.class, "", Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_AUTO_BACKUP_MAX_FILES, Integer.class, Globals.DEFAULT_AUTO_BACKUP_MAX_FILES, Preferences.Visibility.GLOBAL);
    }
}
