package com.xreous.stepperng.preferences.view;

import com.coreyd97.BurpExtenderUtilities.Alignment;
import com.coreyd97.BurpExtenderUtilities.ComponentGroup;
import com.coreyd97.BurpExtenderUtilities.PanelBuilder;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.xreous.stepperng.Globals;
import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequencemanager.SequenceManager;
import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.StaticGlobalVariable;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class OptionsPanel extends JPanel {

    private final SequenceManager sequenceManager;
    private final Preferences preferences;

    public OptionsPanel(SequenceManager sequenceManager){
        this.sequenceManager = sequenceManager;
        this.preferences = Stepper.getPreferences();

        buildPanel();
    }

    private void buildPanel() {
        ComponentGroup configGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Config");
        configGroup.addPreferenceComponent(preferences, Globals.PREF_UPDATE_REQUEST_LENGTH, "Automatically update the Content-Length header");
        configGroup.addPreferenceComponent(preferences, Globals.PREF_ENABLE_SHORTCUT, "Enable Shortcut (Ctrl+Shift+G)");
        configGroup.addPreferenceComponent(preferences, Globals.PREF_ENABLE_UNPROCESSABLE_WARNING, "Warn on non UTF-8 characters in request");

        ComponentGroup toolEnabledGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Allow Variables Usage");
        JCheckBox allToolsCheckbox = toolEnabledGroup.addPreferenceComponent(preferences, Globals.PREF_VARS_IN_ALL_TOOLS, "All Tools");
        JCheckBox proxyCheckbox = toolEnabledGroup.addPreferenceComponent(preferences, Globals.PREF_VARS_IN_PROXY, "Proxy");
        JCheckBox repeaterCheckbox = toolEnabledGroup.addPreferenceComponent(preferences, Globals.PREF_VARS_IN_REPEATER, "Repeater");
        JCheckBox intruderCheckbox = toolEnabledGroup.addPreferenceComponent(preferences, Globals.PREF_VARS_IN_INTRUDER, "Intruder");
        JCheckBox scannerCheckbox = toolEnabledGroup.addPreferenceComponent(preferences, Globals.PREF_VARS_IN_SCANNER, "Scanner");
        JCheckBox sequencerCheckbox = toolEnabledGroup.addPreferenceComponent(preferences, Globals.PREF_VARS_IN_SEQUENCER, "Sequencer");
        JCheckBox extenderCheckbox = toolEnabledGroup.addPreferenceComponent(preferences, Globals.PREF_VARS_IN_EXTENDER, "Extensions");

        { //Set initial states
            boolean individualEnabled = !allToolsCheckbox.isSelected();
            proxyCheckbox.setEnabled(individualEnabled);
            repeaterCheckbox.setEnabled(individualEnabled);
            intruderCheckbox.setEnabled(individualEnabled);
            scannerCheckbox.setEnabled(individualEnabled);
            sequencerCheckbox.setEnabled(individualEnabled);
            extenderCheckbox.setEnabled(individualEnabled);
        }

        allToolsCheckbox.addChangeListener(changeEvent -> {
            boolean individualEnabled = !allToolsCheckbox.isSelected();
            proxyCheckbox.setEnabled(individualEnabled);
            repeaterCheckbox.setEnabled(individualEnabled);
            intruderCheckbox.setEnabled(individualEnabled);
            scannerCheckbox.setEnabled(individualEnabled);
            sequencerCheckbox.setEnabled(individualEnabled);
            extenderCheckbox.setEnabled(individualEnabled);
        });

        GridBagConstraints constraints = toolEnabledGroup.generateNextConstraints(true);
        toolEnabledGroup.add(Box.createHorizontalStrut(175), constraints);

        ComponentGroup importGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Import Sequences");
        importGroup.add(new JButton(new AbstractAction("Import Sequences From File") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setMultiSelectionEnabled(false);
                int result = fileChooser.showOpenDialog(OptionsPanel.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File openingFile = fileChooser.getSelectedFile();
                    byte[] fileContent;
                    try {
                        fileContent = Files.readAllBytes(openingFile.toPath());
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(OptionsPanel.this, "Unable to open file for reading: " + ex.getMessage(),
                                "Unable to Open File", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    importSequencesFromString(new String(fileContent), true);
                }
            }
        }));

        importGroup.add(new JButton(new AbstractAction("Import Sequences As String") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JTextArea inputArea = new JTextArea();
                inputArea.setWrapStyleWord(true);
                inputArea.setLineWrap(true);
                inputArea.setEditable(true);
                JScrollPane scrollPane = new JScrollPane(inputArea);
                scrollPane.setPreferredSize(new Dimension(500, 600));
                scrollPane.setMaximumSize(new Dimension(500, Integer.MAX_VALUE));
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                int result = JOptionPane.showConfirmDialog(OptionsPanel.this, scrollPane,
                        "Import Sequences", JOptionPane.OK_CANCEL_OPTION);
                if(result == JOptionPane.OK_OPTION){
                    importSequencesFromString(inputArea.getText(), true);
                }
            }
        }));

        ComponentGroup exportGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Export Sequences");
        exportGroup.add(new JButton(new AbstractAction("Export Sequences To File") {
            @Override
            public void actionPerformed(ActionEvent e) {
                String sequencesJson = exportSequencesAsString(sequenceManager.getSequences(), true);
                if(sequencesJson == null || sequencesJson.length() == 0) return;

                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setMultiSelectionEnabled(false);
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int result = fileChooser.showSaveDialog(OptionsPanel.this);
                if(result == JFileChooser.APPROVE_OPTION){
                    File saveFile = fileChooser.getSelectedFile();
                    try {
                        Files.write(saveFile.toPath(), sequencesJson.getBytes());
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(OptionsPanel.this, "Unable to write to file: " + ex.getMessage(),
                                "Unable to Save File", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }));

        exportGroup.add(new JButton(new AbstractAction("Export Sequences As String") {
            @Override
            public void actionPerformed(ActionEvent e) {
                String sequencesJson = exportSequencesAsString(sequenceManager.getSequences(), true);
                if(sequencesJson == null || sequencesJson.length() == 0) return;
                JTextArea selectionArea = new JTextArea();
                selectionArea.setWrapStyleWord(true);
                selectionArea.setLineWrap(true);
                selectionArea.setEditable(false);
                selectionArea.setText(sequencesJson);
                JScrollPane scrollPane = new JScrollPane(selectionArea);
                scrollPane.setPreferredSize(new Dimension(500, 600));
                scrollPane.setMaximumSize(new Dimension(500, Integer.MAX_VALUE));
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                JOptionPane.showMessageDialog(OptionsPanel.this, scrollPane,
                        "Exported Sequences", JOptionPane.PLAIN_MESSAGE);
            }
        }));

        PanelBuilder panelBuilder = new PanelBuilder();

        ComponentGroup sessionGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Published Variable Auto-Execution");

        JPanel sessionRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 2));
        sessionRow.add(new JLabel("Validate every N requests:"));
        int currentN = 1;
        try { Object v = preferences.getSetting(Globals.PREF_SESSION_VALIDATE_EVERY_N); if (v instanceof Integer i) currentN = i; } catch (Exception ignored) {}
        JSpinner nSpinner = new JSpinner(new SpinnerNumberModel(currentN, 1, 1000, 1));
        nSpinner.addChangeListener(ce -> preferences.setSetting(Globals.PREF_SESSION_VALIDATE_EVERY_N, (int) nSpinner.getValue()));
        sessionRow.add(nSpinner);
        sessionRow.add(new JLabel("(1 = every request)"));
        sessionGroup.add(sessionRow);

        JTextArea sessionHelp = new JTextArea(
                "When a request contains $VAR: references to published variables, "
                + "Stepper-NG auto-executes the owning sequence to refresh them. "
                + "Use Burp's session handling rules to inject $VAR:seq:name$ into "
                + "headers/cookies. The validation step (if configured) runs first — "
                + "if the session is still valid, the rest of the sequence is skipped. "
                + "Set N > 1 to reduce overhead in scanners/intruder.");
        sessionHelp.setEditable(false);
        sessionHelp.setLineWrap(true);
        sessionHelp.setWrapStyleWord(true);
        sessionHelp.setOpaque(false);
        sessionHelp.setFont(sessionHelp.getFont().deriveFont(Font.ITALIC, sessionHelp.getFont().getSize2D() - 1f));
        sessionHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
        sessionHelp.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        sessionGroup.add(sessionHelp);

        ComponentGroup importGlobalsGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Import Global Variables");
        importGlobalsGroup.add(new JButton(new AbstractAction("Import From File") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setMultiSelectionEnabled(false);
                int result = fileChooser.showOpenDialog(OptionsPanel.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    try {
                        String content = new String(Files.readAllBytes(fileChooser.getSelectedFile().toPath()));
                        importGlobalVariables(content);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(OptionsPanel.this, "Unable to open file: " + ex.getMessage(),
                                "Import Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }));
        importGlobalsGroup.add(new JButton(new AbstractAction("Import As String") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JTextArea inputArea = new JTextArea();
                inputArea.setWrapStyleWord(true);
                inputArea.setLineWrap(true);
                inputArea.setEditable(true);
                JScrollPane scrollPane = new JScrollPane(inputArea);
                scrollPane.setPreferredSize(new Dimension(500, 400));
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                int result = JOptionPane.showConfirmDialog(OptionsPanel.this, scrollPane,
                        "Import Global Variables", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    importGlobalVariables(inputArea.getText());
                }
            }
        }));

        ComponentGroup exportGlobalsGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Export Global Variables");
        exportGlobalsGroup.add(new JButton(new AbstractAction("Export To File") {
            @Override
            public void actionPerformed(ActionEvent e) {
                String json = exportGlobalVariables();
                if (json == null || json.isEmpty()) return;
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int result = fileChooser.showSaveDialog(OptionsPanel.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    try {
                        Files.write(fileChooser.getSelectedFile().toPath(), json.getBytes());
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(OptionsPanel.this, "Unable to write file: " + ex.getMessage(),
                                "Export Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }));
        exportGlobalsGroup.add(new JButton(new AbstractAction("Export As String") {
            @Override
            public void actionPerformed(ActionEvent e) {
                String json = exportGlobalVariables();
                if (json == null || json.isEmpty()) return;
                JTextArea area = new JTextArea(json);
                area.setWrapStyleWord(true);
                area.setLineWrap(true);
                area.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(area);
                scrollPane.setPreferredSize(new Dimension(500, 400));
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                JOptionPane.showMessageDialog(OptionsPanel.this, scrollPane,
                        "Exported Global Variables", JOptionPane.PLAIN_MESSAGE);
            }
        }));

        panelBuilder.setComponentGrid(new JComponent[][]{new JComponent[]{toolEnabledGroup, importGroup},
                                                                new JComponent[]{toolEnabledGroup, exportGroup},
                                                                new JComponent[]{configGroup, importGlobalsGroup},
                                                                new JComponent[]{sessionGroup, exportGlobalsGroup}});
        panelBuilder.setAlignment(Alignment.TOPMIDDLE);
        this.add(panelBuilder.build());
    }

    private void importSequencesFromString(String sequencesJson, boolean displaySelectionDialog){
        Gson gson = Stepper.getGsonProvider().getGson();
        ArrayList<StepSequence> allSequences = null;
        try{
            allSequences = gson.fromJson(sequencesJson, new TypeToken<ArrayList<StepSequence>>(){}.getType());
        }catch (Exception e){
            Stepper.montoya.logging().logToError("Stepper-NG: Failed to parse import JSON: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to parse JSON: " + e.getMessage(),
                    "Import Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if(allSequences == null || allSequences.size() == 0){
            JOptionPane.showMessageDialog(this, "Could not import sequences. " +
                    "Either the JSON is malfored or no sequences could be found in the content.", "Import Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<StepSequence> selectedSequences;
        if(displaySelectionDialog){
            SequenceSelectionDialog dialog = new SequenceSelectionDialog(
                    (Frame) SwingUtilities.getWindowAncestor(this), "Import Sequences", allSequences);
            selectedSequences = dialog.run();
        }else{
            selectedSequences = allSequences;
        }

        for (StepSequence selectedSequence : selectedSequences) {
            this.sequenceManager.addStepSequence(selectedSequence);
        }

    }

    private String exportSequencesAsString(List<StepSequence> sequences, boolean displaySelectionDialog){
        List<StepSequence> selectedSequences;
        if(displaySelectionDialog){
            SequenceSelectionDialog dialog = new SequenceSelectionDialog(
                    (Frame) SwingUtilities.getWindowAncestor(this), "Export Sequences", sequences);
            selectedSequences = dialog.run();
        }else{
            selectedSequences = sequences;
        }

        if(selectedSequences == null) return "";

        Gson gson = Stepper.getGsonProvider().getGson();
        return gson.toJson(selectedSequences, new TypeToken<ArrayList<StepSequence>>(){}.getType());
    }

    private String exportGlobalVariables() {
        DynamicGlobalVariableManager mgr = Stepper.getDynamicGlobalVariableManager();
        if (mgr == null) return "";
        Gson gson = Stepper.getGsonProvider().getGson();
        JsonObject root = new JsonObject();
        root.add("dynamicVariables", gson.toJsonTree(
                new ArrayList<>(mgr.getVariables()), new TypeToken<ArrayList<DynamicGlobalVariable>>(){}.getType()));
        root.add("staticVariables", gson.toJsonTree(
                new ArrayList<>(mgr.getStaticVariables()), new TypeToken<ArrayList<StaticGlobalVariable>>(){}.getType()));
        return gson.toJson(root);
    }

    private void importGlobalVariables(String json) {
        DynamicGlobalVariableManager mgr = Stepper.getDynamicGlobalVariableManager();
        if (mgr == null) return;
        try {
            Gson gson = Stepper.getGsonProvider().getGson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) {
                JOptionPane.showMessageDialog(this, "Invalid JSON.", "Import Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int count = 0;
            if (root.has("dynamicVariables") && root.getAsJsonArray("dynamicVariables") != null) {
                List<DynamicGlobalVariable> dvars = gson.fromJson(
                        root.getAsJsonArray("dynamicVariables"),
                        new TypeToken<ArrayList<DynamicGlobalVariable>>(){}.getType());
                if (dvars != null) {
                    for (DynamicGlobalVariable v : dvars) { mgr.addVariable(v); count++; }
                }
            }
            if (root.has("staticVariables") && root.getAsJsonArray("staticVariables") != null) {
                List<StaticGlobalVariable> svars = gson.fromJson(
                        root.getAsJsonArray("staticVariables"),
                        new TypeToken<ArrayList<StaticGlobalVariable>>(){}.getType());
                if (svars != null) {
                    for (StaticGlobalVariable v : svars) { mgr.addStaticVariable(v); count++; }
                }
            }
            JOptionPane.showMessageDialog(this, "Imported " + count + " global variable(s).",
                    "Import Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            Stepper.montoya.logging().logToError("Stepper-NG: Failed to import global variables: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Failed to parse JSON: " + e.getMessage(),
                    "Import Failed", JOptionPane.ERROR_MESSAGE);
        }
    }


}
