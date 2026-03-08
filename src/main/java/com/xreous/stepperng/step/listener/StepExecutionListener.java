package com.xreous.stepperng.step.listener;

import com.xreous.stepperng.step.StepExecutionInfo;

public interface StepExecutionListener {
    void beforeStepExecution();
    void stepExecuted(StepExecutionInfo stepExecutionInfo);
}
