package com.xreous.stepperng.util.view;

import com.xreous.stepperng.variable.PostExecutionStepVariable;
import com.xreous.stepperng.variable.RegexVariable;
import com.xreous.stepperng.variable.StepVariable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Created by corey on 22/08/17.
 */
public class PostExecutionStepVariableRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if(value instanceof PostExecutionStepVariable) {
            Component c = super.getTableCellRendererComponent(table, ((PostExecutionStepVariable) value).getConditionText(), isSelected, hasFocus, row, column);
            if (value instanceof RegexVariable) {
                if(((RegexVariable) value).getPattern() != null){ //Pattern was valid
                    c.setBackground(Themes.okBackground(table));
                    c.setForeground(Color.BLACK);
                }else if(((RegexVariable) value).getConditionText() != null){ //Pattern was not null and invalid
                    c.setBackground(Themes.badBackground(table));
                    c.setForeground(Color.WHITE);
                }
            }
            return c;
        }else{
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }
}

