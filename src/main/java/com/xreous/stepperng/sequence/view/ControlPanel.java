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
    private final JComboBox<String> postValidationStepCombo;
    private final JSpinner maxFailuresSpinner;
    private final JLabel sessionStatusLabel;
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
                stepSequence.setValidationStepId(null);
            } else {
                int stepIdx = getOriginalStepIndex(validationStepCombo, idx);
                if (stepIdx < 0) return;
                Step selected = stepSequence.getSteps().get(stepIdx);
                // Prevent selecting the same step as post-validation
                if (selected.getStepId().equals(stepSequence.getPostValidationStepId())) return;
                if (stepIdx != 0) {
                    stepSequence.moveStep(stepIdx, 0);
                }
                stepSequence.setValidationStepId(selected.getStepId());
            }

            if (!stepSequence.getSteps().isEmpty()) {
                stepSequence.stepModified(stepSequence.getSteps().get(0));
            }
        });

        postValidationStepCombo = new JComboBox<>();
        refreshPostValidationStepCombo();
        postValidationStepCombo.addActionListener(e -> {
            if (isRefreshing) return;
            int idx = postValidationStepCombo.getSelectedIndex();
            if (idx <= 0) {
                stepSequence.setPostValidationStepId(null);
            } else {
                int stepIdx = getOriginalStepIndex(postValidationStepCombo, idx);
                if (stepIdx < 0) return;
                Step selected = stepSequence.getSteps().get(stepIdx);
                // Prevent selecting the same step as pre-validation
                if (selected.getStepId().equals(stepSequence.getValidationStepId())) return;
                int lastIdx = stepSequence.getSteps().size() - 1;
                if (stepIdx != lastIdx) {
                    stepSequence.moveStep(stepIdx, lastIdx);
                }
                stepSequence.setPostValidationStepId(selected.getStepId());
            }

            if (!stepSequence.getSteps().isEmpty()) {
                stepSequence.stepModified(stepSequence.getSteps().get(stepSequence.getSteps().size() - 1));
            }
        });

        stepSequence.addStepListener(new StepAdapter() {
            @Override public void onStepAdded(Step step) { SwingUtilities.invokeLater(() -> { refreshValidationStepCombo(); refreshPostValidationStepCombo(); updateExecuteButtonState(); }); }
            @Override public void onStepRemoved(Step step) { SwingUtilities.invokeLater(() -> { refreshValidationStepCombo(); refreshPostValidationStepCombo(); updateExecuteButtonState(); }); }
            @Override public void onStepUpdated(Step step) { SwingUtilities.invokeLater(() -> { refreshValidationStepCombo(); refreshPostValidationStepCombo(); updateExecuteButtonState(); }); }
        });

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftPanel.add(new JLabel("Pre-Validation:"));
        validationStepCombo.setToolTipText(
                "Runs before the full sequence. If condition triggers, session is valid and rest is skipped. Moved to first position.");
        leftPanel.add(validationStepCombo);

        maxFailuresSpinner = new JSpinner(new SpinnerNumberModel(
                stepSequence.getMaxConsecutiveFailures(), 1, 99, 1));
        maxFailuresSpinner.setToolTipText("Number of consecutive post-validation failures before pausing the engine");
        maxFailuresSpinner.addChangeListener(e -> {
            stepSequence.setMaxConsecutiveFailures((int) maxFailuresSpinner.getValue());
            if (!stepSequence.getSteps().isEmpty()) {
                stepSequence.stepModified(stepSequence.getSteps().get(0));
            }
        });
        ((JSpinner.DefaultEditor) maxFailuresSpinner.getEditor()).getTextField().setColumns(2);

        sessionStatusLabel = new JLabel();
        updateSessionStatusLabel();

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.add(new JLabel("Post-Validation:"));
        postValidationStepCombo.setToolTipText(
                "Runs after the full sequence to verify session recovery. Pauses task engine on consecutive failures. Moved to last position.");
        rightPanel.add(postValidationStepCombo);
        rightPanel.add(new JLabel("Max fails:"));
        rightPanel.add(maxFailuresSpinner);
        rightPanel.add(Box.createHorizontalStrut(5));
        rightPanel.add(sessionStatusLabel);

        add(leftPanel, BorderLayout.WEST);
        add(executeButton, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        this.stepSequence.addSequenceExecutionListener(this);
    }

    private void refreshValidationStepCombo() {
        isRefreshing = true;
        try {
            String currentId = stepSequence.getValidationStepId();
            String postValId = stepSequence.getPostValidationStepId();
            validationStepCombo.removeAllItems();
            validationStepCombo.addItem("None");
            int selectedComboIdx = 0;
            for (int i = 0; i < stepSequence.getSteps().size(); i++) {
                Step s = stepSequence.getSteps().get(i);
                if (postValId != null && s.getStepId().equals(postValId)) continue;
                validationStepCombo.addItem((i + 1) + ": " + s.getTitle());
                if (currentId != null && s.getStepId().equals(currentId)) {
                    selectedComboIdx = validationStepCombo.getItemCount() - 1;
                }
            }
            validationStepCombo.setSelectedIndex(selectedComboIdx);
        } finally {
            isRefreshing = false;
        }
    }

    private void refreshPostValidationStepCombo() {
        isRefreshing = true;
        try {
            String currentId = stepSequence.getPostValidationStepId();
            String preValId = stepSequence.getValidationStepId();
            postValidationStepCombo.removeAllItems();
            postValidationStepCombo.addItem("None");
            int selectedComboIdx = 0;
            for (int i = 0; i < stepSequence.getSteps().size(); i++) {
                Step s = stepSequence.getSteps().get(i);
                if (preValId != null && s.getStepId().equals(preValId)) continue;
                postValidationStepCombo.addItem((i + 1) + ": " + s.getTitle());
                if (currentId != null && s.getStepId().equals(currentId)) {
                    selectedComboIdx = postValidationStepCombo.getItemCount() - 1;
                }
            }
            postValidationStepCombo.setSelectedIndex(selectedComboIdx);
        } finally {
            isRefreshing = false;
        }
    }

    /**
     * Extracts the original step index from a combo item like "3: Step Title".
     * Returns -1 if not parseable.
     */
    private int getOriginalStepIndex(JComboBox<String> combo, int comboIdx) {
        if (comboIdx <= 0) return -1;
        String item = combo.getItemAt(comboIdx);
        if (item == null) return -1;
        int colonIdx = item.indexOf(':');
        if (colonIdx <= 0) return -1;
        try {
            return Integer.parseInt(item.substring(0, colonIdx).trim()) - 1;
        } catch (NumberFormatException e) {
            return -1;
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
        updateExecuteButtonState();
        updateSessionStatusLabel();
    }

    private void updateExecuteButtonState() {
        if (stepSequence.isExecuting()) return;
        if (stepSequence.isDisabled()) {
            executeButton.setEnabled(false);
            executeButton.setText("Sequence Disabled");
        } else {
            executeButton.setEnabled(true);
            executeButton.setText("Execute Sequence");
        }
    }

    private void updateSessionStatusLabel() {
        SwingUtilities.invokeLater(() -> {
            if (stepSequence.getPostValidationStepId() != null) {
                int failures = stepSequence.getConsecutiveFailures();
                if (failures > 0) {
                    sessionStatusLabel.setText("Post-validation: " + failures + "/" + stepSequence.getMaxConsecutiveFailures() + " failures");
                    sessionStatusLabel.setForeground(new Color(200, 150, 50));
                    sessionStatusLabel.setToolTipText("Post-validation has failed " + failures + " consecutive times");
                } else {
                    sessionStatusLabel.setText("Session OK");
                    sessionStatusLabel.setForeground(new Color(50, 150, 50));
                    sessionStatusLabel.setToolTipText("Post-validation is active");
                }
            } else {
                sessionStatusLabel.setText("");
                sessionStatusLabel.setToolTipText(null);
            }
        });
    }
}
