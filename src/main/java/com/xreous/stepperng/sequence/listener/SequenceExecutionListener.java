package com.xreous.stepperng.sequence.listener;

import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.StepExecutionInfo;

import java.util.List;

public interface SequenceExecutionListener {
    void beforeSequenceStart(List<Step> steps);
    void sequenceStepExecuted(StepExecutionInfo executionInfo);
    void afterSequenceEnd(boolean success);
}
