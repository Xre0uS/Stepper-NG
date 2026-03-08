package com.xreous.stepperng.condition;

public enum ConditionFailAction {
    CONTINUE,
    SKIP_REMAINING,
    GOTO_STEP;

    @Override
    public String toString() {
        return switch (this) {
            case CONTINUE -> "Continue to next step";
            case SKIP_REMAINING -> "Skip remaining steps";
            case GOTO_STEP -> "Go to step…";
        };
    }
}

