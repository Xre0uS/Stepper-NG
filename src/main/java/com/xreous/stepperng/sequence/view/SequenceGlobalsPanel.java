package com.xreous.stepperng.sequence.view;

import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.variable.VariableManager;

import javax.swing.*;
import java.awt.*;

public class SequenceGlobalsPanel extends JPanel {

    private final StepSequence sequence;
    private final VariableManager globalVariableManager;


    public SequenceGlobalsPanel(StepSequence stepSequence){
        this.sequence = stepSequence;
        this.globalVariableManager = this.sequence.getGlobalVariableManager();
        buildPanel();
    }

    private void buildPanel() {
        this.setLayout(new BorderLayout());
        SequenceGlobalsTable table = new SequenceGlobalsTable(this.globalVariableManager);
        this.add(new JScrollPane(table), BorderLayout.CENTER);
        this.add(new SequenceGlobalsControlPanel(this.globalVariableManager, table), BorderLayout.SOUTH);
    }


}
