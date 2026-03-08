package com.xreous.stepperng.util.view;

import com.xreous.stepperng.variable.StepVariable;

import javax.swing.*;
import java.awt.*;

public class StepVariableEditor extends DefaultCellEditor {

    public StepVariableEditor() {
        super(new JTextField());
        this.getComponent().setMinimumSize(new Dimension(100, 100));
    }
}
