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
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;

public class StepperPreferenceFactory extends PreferenceFactory {

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

    @Override
    protected void registerSettings() {
        prefs.registerSetting(Globals.PREF_STEP_SEQUENCES, new TypeToken<ArrayList<StepSequence>>(){}.getType(), Preferences.Visibility.PROJECT);
        prefs.registerSetting(Globals.PREF_DYNAMIC_GLOBAL_VARS, new TypeToken<ArrayList<DynamicGlobalVariable>>(){}.getType(), Preferences.Visibility.PROJECT);
        prefs.registerSetting(Globals.PREF_STATIC_GLOBAL_VARS, new TypeToken<ArrayList<StaticGlobalVariable>>(){}.getType(), Preferences.Visibility.PROJECT);
        prefs.registerSetting(Globals.PREF_PREV_VERSION, String.class, Globals.VERSION, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_ALL_TOOLS, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_EXTENDER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_SEQUENCER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_REPEATER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_PROXY, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_INTRUDER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_VARS_IN_SCANNER, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_UPDATE_REQUEST_LENGTH, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_ENABLE_SHORTCUT, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_ENABLE_UNPROCESSABLE_WARNING, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(Globals.PREF_SESSION_VALIDATE_EVERY_N, Integer.class, 1, Preferences.Visibility.GLOBAL);
    }
}
