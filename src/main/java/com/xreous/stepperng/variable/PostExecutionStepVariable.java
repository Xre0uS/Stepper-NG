package com.xreous.stepperng.variable;

import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.StepExecutionInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.regex.Pattern;

public abstract class PostExecutionStepVariable extends StepVariable {

    PostExecutionStepVariable(String identifier){
        super(identifier);
    }

    public abstract void setCondition(String condition);

    public abstract String getConditionText();

    public abstract void updateVariableAfterExecution(StepExecutionInfo executionInfo);
}
