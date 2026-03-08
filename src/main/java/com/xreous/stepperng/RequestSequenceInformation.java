package com.xreous.stepperng;

import com.xreous.stepperng.sequence.StepSequence;

import java.util.Map;

/**
 * Holds a step sequence reference and any argument overrides parsed from stepper headers.
 *
 * Header format: X-Stepper-Execute-Before: sequenceName: arg1=val1; arg2=val2
 * Argument header: X-Stepper-Argument: key=value
 */
class RequestSequenceInformation {
    public final StepSequence sequence;
    public final Map<String, String> arguments;

    public RequestSequenceInformation(StepSequence sequence, Map<String, String> arguments) {
        this.sequence = sequence;
        this.arguments = arguments;
    }
}

