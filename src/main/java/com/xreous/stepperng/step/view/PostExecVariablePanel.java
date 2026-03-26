package com.xreous.stepperng.step.view;

import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.util.AutoRegexDialog;
import com.xreous.stepperng.util.dialog.VariableCreationDialog;
import com.xreous.stepperng.variable.RegexVariable;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.VariableManager;

import javax.swing.*;
import java.awt.*;

public class PostExecVariablePanel extends VariablePanel {

    private Step step;
    private HttpResponseEditor responseEditor;

    public PostExecVariablePanel(VariableManager variableManager, Step step){
        this(variableManager, step, null);
    }

    public PostExecVariablePanel(VariableManager variableManager, Step step, HttpResponseEditor responseEditor){
        super("Post-Execution Variables", variableManager);
        this.step = step;
        this.responseEditor = responseEditor;
        // Table is created by super() before step is assigned, so set it now
        if (this.variableTable instanceof PostExecutionVariableTable postTable) {
            postTable.setStep(step);
        }
    }

    @Override
    void createVariableTable() {
        this.variableTable = new PostExecutionVariableTable(this.variableManager);
    }

    @Override
    JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new GridLayout(1, 0));

        JButton addVariableButton = new JButton("Add Variable");
        addVariableButton.addActionListener(e -> handleAddVariableEvent());

        JButton autoRegexButton = new JButton("Auto-Regex");
        autoRegexButton.setToolTipText("Highlight text in the response below to auto-generate a regex variable");
        autoRegexButton.addActionListener(e -> handleAutoRegexEvent());

        JButton deleteButton = new JButton("Delete Selected");
        deleteButton.addActionListener(e -> handleDeleteVariableEvent());

        controlPanel.add(addVariableButton);
        controlPanel.add(autoRegexButton);
        controlPanel.add(deleteButton);
        return controlPanel;
    }

    @Override
    void handleAddVariableEvent() {
        VariableCreationDialog dialog = new VariableCreationDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "New Variable", VariableCreationDialog.VariableType.REGEX);
        StepVariable variable = dialog.run();
        if(variable != null) {
            this.variableManager.addVariable(variable);
        }
    }

    private void handleAutoRegexEvent() {
        byte[] responseBytes = step.getResponse();
        if (responseBytes == null || responseBytes.length == 0) {
            JOptionPane.showMessageDialog(this, "No response data yet. Execute the step first.",
                    "No Response", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String responseText = new String(responseBytes);

        String preSelection = null;
        int preSelOffset = -1;
        if (responseEditor != null && responseEditor.selection().isPresent()) {
            var selection = responseEditor.selection().get();
            var range = selection.offsets();
            int start = range.startIndexInclusive();
            int end = range.endIndexExclusive();
            if (start >= 0 && end > start && end <= responseBytes.length) {
                preSelection = new String(responseBytes, start, end - start);
                preSelOffset = start;
            }
        }

        AutoRegexDialog.Result result = AutoRegexDialog.show(
                this, responseText,
                "Auto-Generate Regex Variable", "response",
                preSelection, preSelOffset);

        if (result != null && !result.regex.isEmpty()) {
            String varName = result.variableName.isEmpty()
                    ? "auto_" + System.currentTimeMillis()
                    : result.variableName;
            RegexVariable var = new RegexVariable(varName);
            this.variableManager.addVariable(var);
            var.setCondition(result.regex);
        }
    }

    @Override
    void handleDeleteVariableEvent() {
        if(this.variableTable.getSelectedRow() >= 0) {
            StepVariable variable = this.variableManager.getPostExecutionVariables().get(this.variableTable.getSelectedRow());
            this.variableManager.removeVariable(variable);
        }
    }
}
