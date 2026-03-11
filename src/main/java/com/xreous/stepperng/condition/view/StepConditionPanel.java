package com.xreous.stepperng.condition.view;

import com.xreous.stepperng.condition.ConditionFailAction;
import com.xreous.stepperng.condition.StepCondition;
import com.xreous.stepperng.step.Step;

import javax.swing.*;
import java.awt.*;
import java.util.Vector;

public class StepConditionPanel extends JPanel {

    private final Step step;
    private final JComboBox<StepCondition.ConditionType> typeCombo;
    private final JTextField patternField;
    private final JComboBox<StepCondition.MatchMode> matchModeCombo;
    private final JComboBox<ConditionFailAction> actionCombo;
    private final JComboBox<String> gotoCombo;
    private final JSpinner retrySpinner;
    private final JSpinner delaySpinner;
    private boolean collapsed = true;
    private final JPanel contentPanel;
    private final JButton toggleButton;
    private boolean updating = false;
    private final JLabel validationHint;
    private final JLabel retryLabel;
    private final JLabel delayLabel;
    private final JLabel msLabel;
    private final JLabel thenLabel;
    private final JLabel elseLabel;
    private final JComboBox<ConditionFailAction> elseActionCombo;
    private final JComboBox<String> elseGotoCombo;

    public StepConditionPanel(Step step) {
        super(new BorderLayout());
        this.step = step;

        StepCondition cond = step.getCondition();
        if (cond == null) cond = new StepCondition();

        updating = true;

        typeCombo = new JComboBox<>(StepCondition.ConditionType.values());
        typeCombo.setSelectedItem(cond.getType());

        patternField = new JTextField(cond.getPattern(), 20);

        matchModeCombo = new JComboBox<>(StepCondition.MatchMode.values());
        matchModeCombo.setSelectedItem(cond.getMatchMode());

        actionCombo = new JComboBox<>(ConditionFailAction.values());
        actionCombo.setSelectedItem(cond.getAction());

        gotoCombo = new JComboBox<>();
        refreshStepCombo(gotoCombo);
        selectStepInCombo(gotoCombo, cond.getGotoTarget());

        retrySpinner = new JSpinner(new SpinnerNumberModel(cond.getRetryCount(), 0, 20, 1));
        delaySpinner = new JSpinner(new SpinnerNumberModel((int) cond.getRetryDelayMs(), 0, 30000, 100));

        elseActionCombo = new JComboBox<>(ConditionFailAction.values());
        elseActionCombo.setSelectedItem(cond.getElseAction());

        elseGotoCombo = new JComboBox<>();
        refreshStepCombo(elseGotoCombo);
        selectStepInCombo(elseGotoCombo, cond.getElseGotoTarget());

        Runnable applyAndUpdate = () -> {
            if (updating) return;
            updateFieldVisibility();
            String gotoTarget = "";
            if (gotoCombo.getSelectedItem() != null && gotoCombo.getSelectedIndex() > 0) {
                gotoTarget = extractStepTitle((String) gotoCombo.getSelectedItem());
            }
            String elseGotoTarget = "";
            if (elseGotoCombo.getSelectedItem() != null && elseGotoCombo.getSelectedIndex() > 0) {
                elseGotoTarget = extractStepTitle((String) elseGotoCombo.getSelectedItem());
            }
            StepCondition c = new StepCondition(
                    (StepCondition.ConditionType) typeCombo.getSelectedItem(),
                    patternField.getText(),
                    (StepCondition.MatchMode) matchModeCombo.getSelectedItem(),
                    (ConditionFailAction) actionCombo.getSelectedItem(),
                    gotoTarget,
                    (int) retrySpinner.getValue(),
                    (long) (int) delaySpinner.getValue()
            );
            c.setElseAction((ConditionFailAction) elseActionCombo.getSelectedItem());
            c.setElseGotoTarget(elseGotoTarget);
            this.step.setCondition(c);
            if (this.step.getSequence() != null) this.step.getSequence().stepModified(this.step);
        };

        typeCombo.addActionListener(e -> applyAndUpdate.run());
        matchModeCombo.addActionListener(e -> applyAndUpdate.run());
        patternField.addActionListener(e -> applyAndUpdate.run());
        patternField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) { applyAndUpdate.run(); }
        });
        actionCombo.addActionListener(e -> applyAndUpdate.run());
        gotoCombo.addActionListener(e -> applyAndUpdate.run());
        retrySpinner.addChangeListener(e -> applyAndUpdate.run());
        delaySpinner.addChangeListener(e -> applyAndUpdate.run());
        elseActionCombo.addActionListener(e -> applyAndUpdate.run());
        elseGotoCombo.addActionListener(e -> applyAndUpdate.run());

        contentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        contentPanel.add(new JLabel("If"));
        contentPanel.add(typeCombo);
        contentPanel.add(matchModeCombo);
        contentPanel.add(patternField);

        retryLabel = new JLabel(", retry");
        contentPanel.add(retryLabel);
        contentPanel.add(retrySpinner);
        delayLabel = new JLabel("×");
        contentPanel.add(delayLabel);
        contentPanel.add(delaySpinner);
        msLabel = new JLabel("ms");
        contentPanel.add(msLabel);

        thenLabel = new JLabel(", then");
        contentPanel.add(thenLabel);
        contentPanel.add(actionCombo);
        contentPanel.add(gotoCombo);

        elseLabel = new JLabel(", else");
        contentPanel.add(elseLabel);
        contentPanel.add(elseActionCombo);
        contentPanel.add(elseGotoCombo);

        validationHint = new JLabel("  (validation step — action ignored)");
        validationHint.setFont(validationHint.getFont().deriveFont(Font.ITALIC));
        validationHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        validationHint.setVisible(false);
        contentPanel.add(validationHint);

        updateFieldVisibility();

        boolean hasCondition = step.getCondition() != null && step.getCondition().isConfigured();
        collapsed = !hasCondition;

        toggleButton = new JButton(collapsed ? "▶ Condition" : "▼ Condition");
        toggleButton.setBorderPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleButton.setFocusPainted(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.addActionListener(e -> {
            collapsed = !collapsed;
            contentPanel.setVisible(!collapsed);
            toggleButton.setText(collapsed ? "▶ Condition" : "▼ Condition");
        });

        contentPanel.setVisible(!collapsed);

        add(toggleButton, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        updating = false;
        updateValidationStepState();
    }

    private boolean isValidationStep() {
        if (step.getSequence() == null) return false;
        Integer valIdx = step.getSequence().getValidationStepIndex();
        if (valIdx == null || valIdx < 0) return false;
        Vector<Step> steps = step.getSequence().getSteps();
        return valIdx < steps.size() && steps.get(valIdx) == step;
    }

    private void updateValidationStepState() {
        boolean isVal = isValidationStep();
        validationHint.setVisible(isVal);
        actionCombo.setEnabled(!isVal);
        gotoCombo.setEnabled(!isVal);
        retrySpinner.setEnabled(!isVal);
        delaySpinner.setEnabled(!isVal);
        thenLabel.setEnabled(!isVal);
        retryLabel.setEnabled(!isVal);
        delayLabel.setEnabled(!isVal);
        msLabel.setEnabled(!isVal);
        elseLabel.setEnabled(!isVal);
        elseActionCombo.setEnabled(!isVal);
        elseGotoCombo.setEnabled(!isVal);
    }

    public void refreshValidationState() {
        updateValidationStepState();
    }

    public void refreshStepCombos() {
        refreshStepCombo(gotoCombo);
        refreshStepCombo(elseGotoCombo);
    }

    private void updateFieldVisibility() {
        boolean isAlways = typeCombo.getSelectedItem() == StepCondition.ConditionType.ALWAYS;
        patternField.setVisible(!isAlways);
        matchModeCombo.setVisible(!isAlways);

        // Retry is meaningless for "Always" — condition always triggers on the first attempt
        retryLabel.setVisible(!isAlways);
        retrySpinner.setVisible(!isAlways);

        boolean showGoto = actionCombo.getSelectedItem() == ConditionFailAction.GOTO_STEP;
        gotoCombo.setVisible(showGoto);

        int retries = (int) retrySpinner.getValue();
        boolean showDelay = !isAlways && retries > 0;
        delayLabel.setVisible(showDelay);
        delaySpinner.setVisible(showDelay);
        msLabel.setVisible(showDelay);

        // For "Always", show "→" instead of ", then" since the action always fires unconditionally
        thenLabel.setText(isAlways ? "→" : ", then");

        // Else action is visible when the condition is not "Always" (since Always always triggers)
        boolean showElse = !isAlways;
        elseLabel.setVisible(showElse);
        elseActionCombo.setVisible(showElse);
        boolean showElseGoto = showElse && elseActionCombo.getSelectedItem() == ConditionFailAction.GOTO_STEP;
        elseGotoCombo.setVisible(showElseGoto);

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void refreshStepCombo(JComboBox<String> combo) {
        boolean wasUpdating = updating;
        updating = true;
        String prev = combo.getSelectedItem() != null ? (String) combo.getSelectedItem() : "";
        combo.removeAllItems();
        combo.addItem("(Select step)");
        if (step.getSequence() != null) {
            Vector<Step> steps = step.getSequence().getSteps();
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                if (s == step) continue;
                combo.addItem("Step " + (i + 1) + ": " + s.getTitle());
            }
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).equals(prev)) {
                combo.setSelectedIndex(i);
                break;
            }
        }
        updating = wasUpdating;
    }

    private void selectStepInCombo(JComboBox<String> combo, String target) {
        if (target == null || target.isEmpty()) return;
        for (int i = 1; i < combo.getItemCount(); i++) {
            if (extractStepTitle(combo.getItemAt(i)).equalsIgnoreCase(target)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private String extractStepTitle(String comboItem) {
        if (comboItem == null) return "";
        int colonIdx = comboItem.indexOf(": ");
        return colonIdx >= 0 ? comboItem.substring(colonIdx + 2) : comboItem;
    }
}

