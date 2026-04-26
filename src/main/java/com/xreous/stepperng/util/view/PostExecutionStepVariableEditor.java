package com.xreous.stepperng.util.view;

import com.xreous.stepperng.variable.PostExecutionStepVariable;
import com.xreous.stepperng.variable.RegexVariable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Created by corey on 22/08/17.
 */
public class PostExecutionStepVariableEditor extends DefaultCellEditor {

    public PostExecutionStepVariableEditor() {
        super(new JTextField());
        // Minimum size is intentionally not set — the JTable sizes the editor to the cell bounds.
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        if(value instanceof PostExecutionStepVariable) {
            return super.getTableCellEditorComponent(table, ((PostExecutionStepVariable) value).getConditionText(), isSelected, row, column);
        }else{
            return super.getTableCellEditorComponent(table, value, isSelected, row, column);
        }
    }
}

