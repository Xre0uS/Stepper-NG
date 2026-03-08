package com.xreous.stepperng.step.view;

import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.VariableManager;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

public abstract class VariableControlPanel extends JPanel implements ListSelectionListener {

    private JButton addVariableButton;
    private JButton deleteSelectedVariableButton;

    public VariableControlPanel(){
        super(new GridLayout(1, 0));
        this.addVariableButton = new JButton("Add Variable");
        this.addVariableButton.addActionListener(actionEvent -> {
            handleAddVariableEvent();
        });
        this.deleteSelectedVariableButton = new JButton("Delete Selected Variable");
        this.deleteSelectedVariableButton.addActionListener(actionEvent -> {
            handleDeleteVariableEvent();
        });

        this.add(this.addVariableButton);
        this.add(this.deleteSelectedVariableButton);
    }

    abstract void handleAddVariableEvent();

    abstract void handleDeleteVariableEvent();

    @Override
    public void valueChanged(ListSelectionEvent listSelectionEvent) {
        this.deleteSelectedVariableButton.revalidate();
        this.deleteSelectedVariableButton.repaint();
    }
}
