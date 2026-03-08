package com.xreous.stepperng.variable;

import com.xreous.stepperng.variable.listener.StepVariableListener;

import java.util.ArrayList;
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
            variable.updateFromResponse(responseText, host);
        }
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

