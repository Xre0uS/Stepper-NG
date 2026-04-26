package com.xreous.stepperng.step.view;

import com.xreous.stepperng.variable.VariableManager;

import javax.swing.*;
import java.awt.*;

public abstract class VariablePanel extends JPanel {

    protected final VariableManager variableManager;
    protected JTable variableTable;

    VariablePanel(String title, VariableManager variableManager){
        this.setLayout(new BorderLayout());
        this.variableManager = variableManager;
        createVariableTable();

        JPanel controlPanel = createControlPanel();

        if(title != null) {
            JLabel label = new JLabel(title);
            label.setFont(label.getFont().deriveFont(label.getFont().getSize()+4).deriveFont(Font.BOLD));
            label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 0));
            this.add(label, BorderLayout.NORTH);
        }

        this.add(new JScrollPane(this.variableTable), BorderLayout.CENTER);
        this.add(controlPanel, BorderLayout.SOUTH);
    }

    JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new GridLayout(1, 0));
        JButton addVariableButton = new JButton("Add Variable");
        addVariableButton.addActionListener(actionEvent -> handleAddVariableEvent());
        JButton deleteSelectedVariableButton = new JButton("Delete Selected");
        deleteSelectedVariableButton.addActionListener(actionEvent -> handleDeleteVariableEvent());
        controlPanel.add(addVariableButton);
        controlPanel.add(deleteSelectedVariableButton);
        return controlPanel;
    }

    abstract void createVariableTable();
    abstract void handleAddVariableEvent();
    abstract void handleDeleteVariableEvent();
}
