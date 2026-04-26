package com.xreous.stepperng.variable;

import com.xreous.stepperng.Stepper;

import javax.swing.*;
import java.util.UUID;

public class PromptVariable extends PreExecutionStepVariable {

    public PromptVariable(){
        this(UUID.randomUUID().toString());
    }

    public PromptVariable(String identifier){
        super(identifier);
    }

    @Override
    public String getType() {
        return "Prompt";
    }

    @Override
    public String getValuePreview() {
        return String.format("$PROMPT_VALUE:%s$", getIdentifier());
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void updateVariableBeforeExecution() {
        final String[] result = {null};
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            result[0] = JOptionPane.showInputDialog(Stepper.suiteFrame(), "Enter value for variable \"" + this.identifier + "\": ",
                    "Variable Value", JOptionPane.INFORMATION_MESSAGE);
        } else {
            try {
                javax.swing.SwingUtilities.invokeAndWait(() ->
                    result[0] = JOptionPane.showInputDialog(Stepper.suiteFrame(), "Enter value for variable \"" + this.identifier + "\": ",
                            "Variable Value", JOptionPane.INFORMATION_MESSAGE)
                );
            } catch (Exception e) {
                result[0] = null;
            }
        }
        this.value = result[0] == null ? "" : result[0];
        notifyChanges();
    }
}
