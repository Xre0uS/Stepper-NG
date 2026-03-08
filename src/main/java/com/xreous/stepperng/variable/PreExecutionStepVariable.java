package com.xreous.stepperng.variable;

import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.StepExecutionInfo;

public abstract class PreExecutionStepVariable extends StepVariable {

    PreExecutionStepVariable(String identifier){
        super(identifier);
    }

    public abstract void updateVariableBeforeExecution();
}
