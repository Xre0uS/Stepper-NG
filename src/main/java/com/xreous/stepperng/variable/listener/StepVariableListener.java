package com.xreous.stepperng.variable.listener;

import com.xreous.stepperng.variable.StepVariable;

public interface StepVariableListener {
    void onVariableAdded(StepVariable variable);
    void onVariableRemoved(StepVariable variable);
    void onVariableChange(StepVariable variable);
}
