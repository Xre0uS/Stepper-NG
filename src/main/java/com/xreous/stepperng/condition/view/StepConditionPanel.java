package com.xreous.stepperng.condition.view;

import com.xreous.stepperng.condition.ConditionFailAction;
import com.xreous.stepperng.condition.StepCondition;
import com.xreous.stepperng.step.Step;

import javax.swing.*;
import java.awt.*;
import java.util.List;

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
        gotoCombo.setRenderer(new StepIdHidingRenderer());
        refreshStepCombo(gotoCombo);
        selectStepInComboById(gotoCombo, cond.getGotoTarget());

        retrySpinner = new JSpinner(new SpinnerNumberModel(cond.getRetryCount(), 0, 20, 1));
        delaySpinner = new JSpinner(new SpinnerNumberModel((int) cond.getRetryDelayMs(), 0, 30000, 100));

        elseActionCombo = new JComboBox<>(ConditionFailAction.values());
        elseActionCombo.setSelectedItem(cond.getElseAction());

        elseGotoCombo = new JComboBox<>();
        elseGotoCombo.setRenderer(new StepIdHidingRenderer());
        refreshStepCombo(elseGotoCombo);
        selectStepInComboById(elseGotoCombo, cond.getElseGotoTarget());

        Runnable applyAndUpdate = () -> {
            if (updating) return;
            updateFieldVisibility();
            String gotoTarget = extractStepId((String) gotoCombo.getSelectedItem());
            String elseGotoTarget = extractStepId((String) elseGotoCombo.getSelectedItem());
            StepCondition c = new StepCondition(
                    (StepCondition.ConditionType) typeCombo.getSelectedItem(),
                    patternField.getText(),
                    (StepCondition.MatchMode) matchModeCombo.getSelectedItem(),
                    (ConditionFailAction) actionCombo.getSelectedItem(),
                    gotoTarget != null ? gotoTarget : "",
                    (int) retrySpinner.getValue(),
                    (long) (int) delaySpinner.getValue()
            );
            c.setElseAction((ConditionFailAction) elseActionCombo.getSelectedItem());
            c.setElseGotoTarget(elseGotoTarget != null ? elseGotoTarget : "");
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

        validationHint = new JLabel("  (validation step - action ignored)");
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
        String valId = step.getSequence().getValidationStepId();
        if (valId != null && step.getStepId().equals(valId)) return true;
        return isPostValidationStep();
    }

    private boolean isPostValidationStep() {
        if (step.getSequence() == null) return false;
        String postValId = step.getSequence().getPostValidationStepId();
        return postValId != null && step.getStepId().equals(postValId);
    }

    private void updateValidationStepState() {
        boolean isVal = isValidationStep();
        if (isVal) {
            validationHint.setText(isPostValidationStep()
                    ? "  (post-validation step - action ignored)"
                    : "  (pre-validation step - action ignored)");
        }
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
        StepCondition cond = step.getCondition();
        String currentGotoId = cond != null ? cond.getGotoTarget() : "";
        String currentElseGotoId = cond != null ? cond.getElseGotoTarget() : "";
        refreshStepCombo(gotoCombo, currentGotoId);
        refreshStepCombo(elseGotoCombo, currentElseGotoId);
    }

    private void updateFieldVisibility() {
        boolean isAlways = typeCombo.getSelectedItem() == StepCondition.ConditionType.ALWAYS;
        patternField.setVisible(!isAlways);
        matchModeCombo.setVisible(!isAlways);

        retryLabel.setVisible(!isAlways);
        retrySpinner.setVisible(!isAlways);

        boolean showGoto = actionCombo.getSelectedItem() == ConditionFailAction.GOTO_STEP;
        gotoCombo.setVisible(showGoto);

        int retries = (int) retrySpinner.getValue();
        boolean showDelay = !isAlways && retries > 0;
        delayLabel.setVisible(showDelay);
        delaySpinner.setVisible(showDelay);
        msLabel.setVisible(showDelay);

        thenLabel.setText(isAlways ? "→" : ", then");

        boolean showElse = !isAlways;
        elseLabel.setVisible(showElse);
        elseActionCombo.setVisible(showElse);
        boolean showElseGoto = showElse && elseActionCombo.getSelectedItem() == ConditionFailAction.GOTO_STEP;
        elseGotoCombo.setVisible(showElseGoto);

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private static final String ID_PREFIX = " [";
    private static final String ID_SUFFIX = "]";

    private void refreshStepCombo(JComboBox<String> combo, String selectedStepId) {
        boolean wasUpdating = updating;
        updating = true;
        combo.removeAllItems();
        combo.addItem("(Select step)");
        if (step.getSequence() != null) {
            List<Step> steps = step.getSequence().getSteps();
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                if (s == step) continue;
                combo.addItem("Step " + (i + 1) + ": " + s.getTitle() + ID_PREFIX + s.getStepId() + ID_SUFFIX);
            }
        }
        selectStepInComboById(combo, selectedStepId);
        updating = wasUpdating;
    }

    /** For backward compat: also accept refreshStepCombo without an explicit ID (uses condition's stored ID) */
    private void refreshStepCombo(JComboBox<String> combo) {
        StepCondition cond = step.getCondition();
        String id = "";
        if (cond != null) {
            id = (combo == gotoCombo) ? cond.getGotoTarget() : cond.getElseGotoTarget();
        }
        refreshStepCombo(combo, id);
    }

    private void selectStepInComboById(JComboBox<String> combo, String stepId) {
        if (stepId == null || stepId.isEmpty()) return;
        for (int i = 1; i < combo.getItemCount(); i++) {
            String itemId = extractStepId(combo.getItemAt(i));
            if (itemId != null && itemId.equals(stepId)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        for (int i = 1; i < combo.getItemCount(); i++) {
            String itemTitle = extractStepTitle(combo.getItemAt(i));
            if (itemTitle.equalsIgnoreCase(stepId)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private String extractStepId(String comboItem) {
        if (comboItem == null) return null;
        int start = comboItem.lastIndexOf(ID_PREFIX);
        int end = comboItem.lastIndexOf(ID_SUFFIX);
        if (start >= 0 && end > start) {
            return comboItem.substring(start + ID_PREFIX.length(), end);
        }
        return null;
    }

    private String extractStepTitle(String comboItem) {
        if (comboItem == null) return "";
        int idStart = comboItem.lastIndexOf(ID_PREFIX);
        String withoutId = idStart >= 0 ? comboItem.substring(0, idStart) : comboItem;
        int colonIdx = withoutId.indexOf(": ");
        return colonIdx >= 0 ? withoutId.substring(colonIdx + 2).trim() : withoutId.trim();
    }

    private class StepIdHidingRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            String display = value != null ? value.toString() : "";
            int idStart = display.lastIndexOf(ID_PREFIX);
            if (idStart >= 0) {
                display = display.substring(0, idStart);
            }
            return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
        }
    }
}

