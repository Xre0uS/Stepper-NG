package com.xreous.stepperng.preferences.view;

import com.xreous.stepperng.sequence.StepSequence;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SequenceSelectionDialog extends JDialog {

    private final List<StepSequence> allSequences;
    private List<StepSequence> selectedSequences;
    private SequenceSelectionTable sequenceSelectionTable;
    private final boolean showGlobalsOption;
    private JCheckBox globalsCheckbox;
    private boolean cancelled = false;

    public SequenceSelectionDialog(Frame owner, String title, List<StepSequence> sequences){
        this(owner, title, sequences, false);
    }

    public SequenceSelectionDialog(Frame owner, String title, List<StepSequence> sequences, boolean showGlobalsOption){
        super(owner, title, true);
        this.allSequences = sequences;
        this.showGlobalsOption = showGlobalsOption;

        buildDialog();
        pack();
        setMinimumSize(new Dimension(400, 250));
        setSize(new Dimension(550, 350));
        setLocationRelativeTo(owner);
    }

    private void buildDialog(){
        BorderLayout borderLayout = new BorderLayout();
        borderLayout.setHgap(10);
        borderLayout.setVgap(10);
        JPanel wrapper = new JPanel(borderLayout);
        wrapper.setBorder(BorderFactory.createEmptyBorder(7,7,7,7));
        wrapper.add(new JLabel("Select items to include: "), BorderLayout.NORTH);

        this.sequenceSelectionTable = new SequenceSelectionTable(this.allSequences);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JScrollPane(this.sequenceSelectionTable), BorderLayout.CENTER);

        if (showGlobalsOption) {
            globalsCheckbox = new JCheckBox("Include Global Variables (static + dynamic)", true);
            globalsCheckbox.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
            centerPanel.add(globalsCheckbox, BorderLayout.SOUTH);
        }

        wrapper.add(centerPanel, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(actionEvent -> {
            selectedSequences = this.sequenceSelectionTable.getSelectedSequences();
            this.setVisible(false);
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(actionEvent -> {
            selectedSequences = null;
            cancelled = true;
            this.setVisible(false);
        });

        okButton.setMinimumSize(new Dimension(100,35));
        okButton.setPreferredSize(new Dimension(100,35));
        cancelButton.setMinimumSize(new Dimension(100,35));
        cancelButton.setPreferredSize(new Dimension(100,35));

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

    public List<StepSequence> run(){
        this.setVisible(true);
        return this.selectedSequences;
    }

    public boolean isGlobalsSelected() {
        return showGlobalsOption && globalsCheckbox != null && globalsCheckbox.isSelected();
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
