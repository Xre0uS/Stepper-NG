package com.xreous.stepperng.exception;

public class SequenceCancelledException extends SequenceExecutionException {
    public SequenceCancelledException(String cause){
        super(cause);
    }
}
