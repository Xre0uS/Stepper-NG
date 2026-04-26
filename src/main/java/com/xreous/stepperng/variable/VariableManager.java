package com.xreous.stepperng.variable;

import com.xreous.stepperng.variable.listener.StepVariableListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public abstract class VariableManager {
    // CoW so HTTP worker threads (passthrough sync, DVAR/GVAR replacement) can iterate
    // while the EDT mutates via addVariable/removeVariable without CME.
    protected final List<StepVariable> variables;
    protected final List<StepVariableListener> variableListeners;

    public VariableManager(){
        this.variables = new CopyOnWriteArrayList<>();
        this.variableListeners = new CopyOnWriteArrayList<>();
    }

    public List<StepVariable> getVariables() {
        return variables;
    }

    public List<PostExecutionStepVariable> getPostExecutionVariables(){
        return variables.stream()
            .filter(var -> var instanceof PostExecutionStepVariable)
            .map(stepVariable -> (PostExecutionStepVariable) stepVariable)
            .collect(Collectors.toList());
    }

    public List<PreExecutionStepVariable> getPreExecutionVariables(){
        return variables.stream()
                .filter(var -> var instanceof PreExecutionStepVariable)
                .map(stepVariable -> (PreExecutionStepVariable) stepVariable)
                .collect(Collectors.toList());
    }

    public void addVariableListener(StepVariableListener listener){
        this.variableListeners.add(listener);
    }

    public void removeVariableListener(StepVariableListener listener){
        this.variableListeners.remove(listener);
    }

    public void addVariable(StepVariable variable){
        this.variables.add(variable);
        variable.setVariableManager(this);
        for (StepVariableListener listener : this.variableListeners) {
            try {
                listener.onVariableAdded(variable);
            }catch (Exception e){
                try { com.xreous.stepperng.Stepper.montoya.logging().logToError("Stepper-NG: Variable listener error (add): " + e.getMessage()); } catch (Exception ignored) {}
            }
        }
    }

    public void removeVariable(StepVariable variable){
        this.variables.remove(variable);
        variable.setVariableManager(null);
        for (StepVariableListener listener : this.variableListeners) {
            try {
                listener.onVariableRemoved(variable);
            }catch (Exception e){
                try { com.xreous.stepperng.Stepper.montoya.logging().logToError("Stepper-NG: Variable listener error (remove): " + e.getMessage()); } catch (Exception ignored) {}
            }
        }
    }

    public void onVariableChange(StepVariable variable){
        for (StepVariableListener listener : this.variableListeners) {
            try {
                listener.onVariableChange(variable);
            }catch(Exception e){
                try { com.xreous.stepperng.Stepper.montoya.logging().logToError("Stepper-NG: Variable listener error (change): " + e.getMessage()); } catch (Exception ignored) {}
            }
        }
    }
}
