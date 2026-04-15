package com.xreous.stepperng.sequencemanager.listener;

import com.xreous.stepperng.sequence.StepSequence;

public interface StepSequenceListener {
    void onStepSequenceAdded(StepSequence sequence);
    void onStepSequenceRemoved(StepSequence sequence);
    default void onStepSequenceModified(StepSequence sequence) {}
}
