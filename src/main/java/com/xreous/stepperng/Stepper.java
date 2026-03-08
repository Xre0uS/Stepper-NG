package com.xreous.stepperng;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.coreyd97.BurpExtenderUtilities.DefaultGsonProvider;
import com.coreyd97.BurpExtenderUtilities.IGsonProvider;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.xreous.stepperng.preferences.StepperPreferenceFactory;
import com.xreous.stepperng.sequencemanager.SequenceManager;
import com.xreous.stepperng.util.variablereplacementstab.VariableReplacementsTabFactory;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Stepper implements BurpExtension {

    public static Stepper instance;
    private static StepperUI ui;
    public static MontoyaApi montoya;
    public static IGsonProvider gsonProvider = new DefaultGsonProvider();
    private static Preferences preferences;
    private static SequenceManager sequenceManager;
    private static DynamicGlobalVariableManager dynamicGlobalVariableManager;

    private StateManager stateManager;
    private MessageProcessor messageProcessor;

    public Stepper(){
        Stepper.instance = this;
    }

    public static Stepper getInstance() { return instance; }
    public static SequenceManager getSequenceManager(){ return sequenceManager; }
    public static DynamicGlobalVariableManager getDynamicGlobalVariableManager() { return dynamicGlobalVariableManager; }
    public static Preferences getPreferences() { return preferences; }
    public static StepperUI getUI() { return ui; }
    public static IGsonProvider getGsonProvider() { return gsonProvider; }

    @Override
    public void initialize(MontoyaApi api) {
        Stepper.montoya = api;
        api.extension().setName(Globals.EXTENSION_NAME);
        api.logging().logToOutput("Stepper-NG " + Globals.VERSION + " initializing...");

        try {
            Stepper.preferences = new StepperPreferenceFactory(Globals.EXTENSION_NAME, gsonProvider, api).buildPreferences();
        } catch (Exception e) {
            api.logging().logToError("Stepper-NG: Failed to load preferences: " + getStackTrace(e));
        }

        Stepper.sequenceManager = new SequenceManager();
        Stepper.dynamicGlobalVariableManager = new DynamicGlobalVariableManager();

        this.stateManager = new StateManager(sequenceManager, preferences, dynamicGlobalVariableManager);
        try {
            this.stateManager.loadSavedSequences();
            this.stateManager.loadDynamicGlobalVars();
        } catch (Exception e) {
            api.logging().logToError("Stepper-NG: Failed to load saved data: " + getStackTrace(e));
        }
        this.messageProcessor = new MessageProcessor(sequenceManager, preferences, dynamicGlobalVariableManager);

        try {
            api.userInterface().registerHttpRequestEditorProvider(new VariableReplacementsTabFactory(sequenceManager));
        } catch (Exception e) {
            api.logging().logToError("Stepper-NG: Failed to register editor provider: " + getStackTrace(e));
        }
        try {
            api.userInterface().registerContextMenuItemsProvider(new ContextMenuFactory(sequenceManager));
        } catch (Exception e) {
            api.logging().logToError("Stepper-NG: Failed to register context menu: " + getStackTrace(e));
        }
        try {
            api.http().registerHttpHandler(messageProcessor);
        } catch (Exception e) {
            api.logging().logToError("Stepper-NG: Failed to register HTTP handler: " + getStackTrace(e));
        }
        api.extension().registerUnloadingHandler(stateManager);

        try {
            ui = new StepperUI(sequenceManager, dynamicGlobalVariableManager);
            api.userInterface().registerSuiteTab(Globals.EXTENSION_NAME, ui.getUiComponent());
            api.logging().logToOutput("Stepper-NG " + Globals.VERSION + " loaded successfully.");
        } catch (Exception e) {
            api.logging().logToError("Stepper-NG: Failed to register UI tab: " + getStackTrace(e));
        }
    }

    private static String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
