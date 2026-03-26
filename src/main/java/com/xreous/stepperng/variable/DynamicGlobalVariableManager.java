package com.xreous.stepperng.variable;

import com.xreous.stepperng.variable.listener.StepVariableListener;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DynamicGlobalVariableManager {

    private final CopyOnWriteArrayList<DynamicGlobalVariable> variables = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<StaticGlobalVariable> staticVariables = new CopyOnWriteArrayList<>();
    private final List<StepVariableListener> listeners = new CopyOnWriteArrayList<>();

    public void addVariable(DynamicGlobalVariable variable) {
        variables.add(variable);
        for (StepVariableListener listener : listeners) listener.onVariableAdded(variable);
    }

    public void removeVariable(DynamicGlobalVariable variable) {
        variables.remove(variable);
        for (StepVariableListener listener : listeners) listener.onVariableRemoved(variable);
    }

    public List<DynamicGlobalVariable> getVariables() {
        return Collections.unmodifiableList(variables);
    }

    public void addStaticVariable(StaticGlobalVariable variable) {
        staticVariables.add(variable);
        for (StepVariableListener listener : listeners) listener.onVariableAdded(variable);
    }

    public void removeStaticVariable(StaticGlobalVariable variable) {
        staticVariables.remove(variable);
        for (StepVariableListener listener : listeners) listener.onVariableRemoved(variable);
    }

    public List<StaticGlobalVariable> getStaticVariables() {
        return Collections.unmodifiableList(staticVariables);
    }

    public void processResponse(String responseText, String host) {
        for (DynamicGlobalVariable variable : variables) {
            try {
                variable.updateFromResponse(responseText, host);
            } catch (Exception e) {
                try { com.xreous.stepperng.Stepper.montoya.logging().logToError("Stepper-NG: DVAR response capture error for '" + variable.getIdentifier() + "': " + e.getMessage()); } catch (Exception ignored) {}
            }
        }
    }

    public void processRequest(String requestText, String host) {
        for (DynamicGlobalVariable variable : variables) {
            try {
                variable.updateFromRequest(requestText, host);
            } catch (Exception e) {
                try { com.xreous.stepperng.Stepper.montoya.logging().logToError("Stepper-NG: DVAR request capture error for '" + variable.getIdentifier() + "': " + e.getMessage()); } catch (Exception ignored) {}
            }
        }
    }


    public boolean hasRequestCaptureDvars() {
        for (DynamicGlobalVariable variable : variables) {
            if (variable.isCaptureFromRequests()) return true;
        }
        return false;
    }

    public void addVariableListener(StepVariableListener listener) {
        listeners.add(listener);
    }

    public void removeVariableListener(StepVariableListener listener) {
        listeners.remove(listener);
    }

    public void notifyVariableChanged(StepVariable variable) {
        for (StepVariableListener listener : listeners) listener.onVariableChange(variable);
    }

    public void clear() {
        variables.clear();
        staticVariables.clear();
    }
}

