package com.xreous.stepperng.util.dialog;

import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.variable.PromptVariable;
import com.xreous.stepperng.variable.RegexVariable;
import com.xreous.stepperng.variable.StepVariable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.UUID;

public class VariableCreationDialog extends JDialog {

    public enum VariableType { PROMPT, REGEX }
    StepVariable variable;

    public VariableCreationDialog(Frame owner, String title, VariableType type){
        super(owner, title, true);
        buildDialog(type);
        pack();
        Dimension pref = getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        // Ensure the input field gets a usable typing width even with short default button text.
        int minW = Math.max(pref.width, fm.charWidth('M') * 42);
        setMinimumSize(new Dimension(minW, pref.height));
        setSize(getMinimumSize());
        setLocationRelativeTo(owner);
    }

    private void buildDialog(VariableType type){
        BorderLayout borderLayout = new BorderLayout();
        borderLayout.setHgap(10);
        borderLayout.setVgap(10);
        JPanel wrapper = new JPanel(borderLayout);
        wrapper.setBorder(BorderFactory.createEmptyBorder(7,7,7,7));
        JLabel variableNameLabel = new JLabel("Variable Name: ");
        JTextField variableNameInput = new JTextField("");
        JPanel namePanel = new JPanel(new BorderLayout());
        namePanel.add(variableNameLabel, BorderLayout.WEST);
        namePanel.add(variableNameInput, BorderLayout.CENTER);
        wrapper.add(namePanel, BorderLayout.NORTH);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(actionEvent -> {
            String variableName = variableNameInput.getText();
            if(variableName.equalsIgnoreCase("")){
                variableName = UUID.randomUUID().toString();
            }
            switch (type) {
                case PROMPT:
                    variable = new PromptVariable(variableName);
                    break;
                case REGEX:
                    variable = new RegexVariable(variableName);
                    break;
            }
            this.setVisible(false);
        });

        variableNameInput.addActionListener(e -> okButton.doClick());

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(actionEvent -> {
            this.variable = null;
            this.setVisible(false);
        });

        // Buttons size themselves from their text + LAF insets — no magic dimensions.

        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.weightx = 100;
        controlPanel.add(new JPanel(), gbc);
        gbc.weightx = 0;
        controlPanel.add(okButton, gbc);
        controlPanel.add(cancelButton, gbc);
        wrapper.add(controlPanel, BorderLayout.SOUTH);
        this.add(wrapper);
    }

    public StepVariable run(){
        this.setVisible(true);
        return this.variable;
    }

}
