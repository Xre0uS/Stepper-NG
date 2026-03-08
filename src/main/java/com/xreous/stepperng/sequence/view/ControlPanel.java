package com.xreous.stepperng.sequence.view;

import com.xreous.stepperng.sequence.listener.SequenceExecutionListener;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.StepExecutionInfo;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.step.listener.StepAdapter;
import com.xreous.stepperng.step.listener.StepListener;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ControlPanel extends JPanel implements SequenceExecutionListener {

    private final StepSequence stepSequence;
    private final JButton executeButton;
    private final JButton cancelButton;
    private final JComboBox<String> validationStepCombo;
    private boolean isRefreshing = false;
    private int stepsToExecute;
    private int stepsExecuted;

    public ControlPanel(StepSequence stepSequence){
        this.stepSequence = stepSequence;
        this.setLayout(new BorderLayout());

        this.executeButton = new JButton("Execute Sequence");
        this.executeButton.addActionListener(e -> this.stepSequence.executeAsync());

        this.cancelButton = new JButton("Cancel");

        validationStepCombo = new JComboBox<>();
        refreshValidationStepCombo();
        validationStepCombo.addActionListener(e -> {
            if (isRefreshing) return;
            int idx = validationStepCombo.getSelectedIndex();
            if (idx <= 0) {
                stepSequence.setValidationStepIndex(null);
            } else {
                int stepIdx = idx - 1;
                if (stepIdx != 0) {
                    stepSequence.moveStep(stepIdx, 0);
                }
                stepSequence.setValidationStepIndex(0);
            }

            if (!stepSequence.getSteps().isEmpty()) {
                stepSequence.stepModified(stepSequence.getSteps().get(0));
            }
        });

        stepSequence.addStepListener(new StepAdapter() {
            @Override public void onStepAdded(Step step) { SwingUtilities.invokeLater(() -> refreshValidationStepCombo()); }
            @Override public void onStepRemoved(Step step) { SwingUtilities.invokeLater(() -> refreshValidationStepCombo()); }
            @Override public void onStepUpdated(Step step) { SwingUtilities.invokeLater(() -> refreshValidationStepCombo()); }
        });

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftPanel.add(new JLabel("Validation Step:"));
        validationStepCombo.setToolTipText(
                "<html>Runs this step first before the full sequence.<br><br>"
                + "Configure a condition on the step that describes a <b>valid session</b>,<br>"
                + "e.g. <i>If status line matches <code>200</code></i><br><br>"
                + "If the condition <b>triggers</b> → session is valid → rest of sequence is skipped.<br>"
                + "If the condition <b>does not trigger</b> → session is invalid → full sequence runs.<br><br>"
                + "The step must have a non-Always condition configured.</html>");
        leftPanel.add(validationStepCombo);

        add(leftPanel, BorderLayout.WEST);
        add(executeButton, BorderLayout.CENTER);
        this.stepSequence.addSequenceExecutionListener(this);
    }

    private void refreshValidationStepCombo() {
        isRefreshing = true;
        try {
            Integer current = stepSequence.getValidationStepIndex();
            validationStepCombo.removeAllItems();
            validationStepCombo.addItem("None");
            for (int i = 0; i < stepSequence.getSteps().size(); i++) {
                Step s = stepSequence.getSteps().get(i);
                validationStepCombo.addItem((i + 1) + ": " + s.getTitle());
            }
            if (current != null && current >= 0 && current < stepSequence.getSteps().size()) {
                validationStepCombo.setSelectedIndex(current + 1);
            } else {
                validationStepCombo.setSelectedIndex(0);
            }
        } finally {
            isRefreshing = false;
        }
    }

    @Override
    public void beforeSequenceStart(List<Step> steps) {
        this.stepsToExecute = (int) steps.stream().filter(Step::isEnabled).count();
        this.stepsExecuted = 0;
        this.executeButton.setEnabled(false);
        this.executeButton.setText("Executing... (" + stepsExecuted + "/" + stepsToExecute + ")");
    }

    @Override
    public void sequenceStepExecuted(StepExecutionInfo stepExecutionInfo) {
        this.stepsExecuted++;
        this.executeButton.setText("Executing... (" + stepsExecuted + "/" + stepsToExecute + ")");
    }

    @Override
    public void afterSequenceEnd(boolean success) {
        this.executeButton.setEnabled(true);
        this.executeButton.setText("Execute Sequence");
    }
}
