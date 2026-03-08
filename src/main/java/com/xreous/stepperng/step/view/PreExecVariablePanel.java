package com.xreous.stepperng.step.view;

import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.util.dialog.VariableCreationDialog;
import com.xreous.stepperng.variable.VariableManager;
import com.xreous.stepperng.variable.StepVariable;

import javax.swing.*;
import java.awt.*;

public class PreExecVariablePanel extends VariablePanel {

    public PreExecVariablePanel(VariableManager variableManager, Step step){
        super("Pre-Execution Variables", variableManager);
        if (this.variableTable instanceof PreExecutionVariableTable preTable) {
            preTable.setStep(step);
        }
    }

    @Override
    void createVariableTable() {
        this.variableTable = new PreExecutionVariableTable(this.variableManager);
    }

    @Override
    void handleAddVariableEvent() {
        VariableCreationDialog dialog = new VariableCreationDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "New Variable", VariableCreationDialog.VariableType.PROMPT);
        StepVariable variable = dialog.run();
        if(variable != null) {
            this.variableManager.addVariable(variable);
        }
    }

    @Override
    void handleDeleteVariableEvent() {
        if(this.variableTable.getSelectedRow() >= 0) {
            StepVariable variable = this.variableManager.getPreExecutionVariables().get(this.variableTable.getSelectedRow());
            this.variableManager.removeVariable(variable);
        }
    }
}
