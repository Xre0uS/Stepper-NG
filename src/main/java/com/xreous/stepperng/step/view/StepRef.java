package com.xreous.stepperng.step.view;

import com.xreous.stepperng.step.Step;

import javax.swing.*;
import java.awt.*;

/** Typed combo-box entry for step pointers (validation, post-validation, condition goto). */
public final class StepRef {

    public static final StepRef NONE_VALIDATION = new StepRef(null, -1, "None");
    public static final StepRef NONE_GOTO = new StepRef(null, -1, "(Select step)");

    private final Step step;
    private final int displayIndex;
    private final String displayLabel;

    public StepRef(Step step, int displayIndex) {
        this(step, displayIndex, null);
    }

    private StepRef(Step step, int displayIndex, String displayLabel) {
        this.step = step;
        this.displayIndex = displayIndex;
        this.displayLabel = displayLabel;
    }

    public Step getStep() { return step; }
    public String getStepId() { return step != null ? step.getStepId() : null; }

    @Override public String toString() {
        if (displayLabel != null) return displayLabel;
        if (step == null) return "";
        return displayIndex + ": " + step.getTitle();
    }

    public static final class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                                                                int index, boolean isSelected, boolean cellHasFocus) {
            String text = value instanceof StepRef r ? r.toString() : (value == null ? "" : value.toString());
            return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
        }
    }
}



