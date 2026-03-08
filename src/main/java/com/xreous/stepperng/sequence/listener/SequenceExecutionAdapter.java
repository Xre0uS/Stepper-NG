package com.xreous.stepperng.sequence.listener;

import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.StepExecutionInfo;

import java.util.List;

public abstract class SequenceExecutionAdapter implements SequenceExecutionListener {

    @Override
    public void beforeSequenceStart(List<Step> steps) {

    }

    @Override
    public void sequenceStepExecuted(StepExecutionInfo executionInfo) {

    }

    @Override
    public void afterSequenceEnd(boolean success) {

    }
}
