package com.xreous.stepperng.step;

import com.xreous.stepperng.variable.*;

public class StepVariableManager extends VariableManager {

    private final Step step;

    StepVariableManager(Step step){
        super();
        this.step = step;
    }

    public Step getStep() {
        return step;
    }

    public void updateVariablesBeforeExecution(){
        for (StepVariable variable : this.variables) {
            if(variable instanceof PreExecutionStepVariable)
                ((PreExecutionStepVariable) variable).updateVariableBeforeExecution();
        }
    }

    public void updateVariablesAfterExecution(StepExecutionInfo executionInfo){
        for (StepVariable variable : this.variables) {
            if(variable instanceof PostExecutionStepVariable)
            ((PostExecutionStepVariable) variable).updateVariableAfterExecution(executionInfo);
        }
    }

    public void updateVariableWithPreviousExecutionResult(PostExecutionStepVariable variable){
        if (this.step.getLastExecutionResult() != null) {
            variable.updateVariableAfterExecution(this.step.getLastExecutionResult());
        } else if (variable instanceof RegexVariable regexVar) {
            regexVar.updateFromResponseBytes(this.step.getResponse());
        }
    }
}
