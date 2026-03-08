package com.xreous.stepperng.sequence;

import com.xreous.stepperng.variable.VariableManager;

public class GlobalVariableManager extends VariableManager {

    private final StepSequence sequence;

    public GlobalVariableManager(StepSequence sequence){
        this.sequence = sequence;
    }
}
