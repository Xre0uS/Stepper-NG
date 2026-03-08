package com.xreous.stepperng.step.listener;

import com.xreous.stepperng.step.Step;

public interface StepListener {
    void onStepAdded(Step step);
    void onStepUpdated(Step step);
    void onStepRemoved(Step step);
}
