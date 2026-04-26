package com.xreous.stepperng;
import com.xreous.stepperng.sequence.StepSequence;
import java.util.Map;
/** Parsed X-Stepper-Execute-{Before,After} header payload. */
class RequestSequenceInformation {
    public final StepSequence sequence;
    public final Map<String, String> arguments;
    /** Zero-based inclusive upper bound when the header used the {@code {start:end}} form. -1 means "run all". */
    public final int upToIndexInclusive;
    public RequestSequenceInformation(StepSequence sequence, Map<String, String> arguments) {
        this(sequence, arguments, -1);
    }
    public RequestSequenceInformation(StepSequence sequence, Map<String, String> arguments, int upToIndexInclusive) {
        this.sequence = sequence;
        this.arguments = arguments;
        this.upToIndexInclusive = upToIndexInclusive;
    }
}
