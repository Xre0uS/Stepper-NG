package com.xreous.stepperng.condition.view;

import com.xreous.stepperng.condition.ConditionFailAction;
import com.xreous.stepperng.condition.StepCondition;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.view.StepRef;
import com.xreous.stepperng.util.view.Themes;
import com.xreous.stepperng.util.view.WrapLayout;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class StepConditionPanel extends JPanel {

    private final Step step;
    private final JComboBox<StepCondition.ConditionType> typeCombo;
    private final JTextField patternField;
    private final JComboBox<StepCondition.MatchMode> matchModeCombo;
    private final JComboBox<ConditionFailAction> actionCombo;
    private final JComboBox<StepRef> gotoCombo;
    private final JSpinner retrySpinner;
    private final JSpinner delaySpinner;
    private final JComboBox<ConditionFailAction> elseActionCombo;
    private final JComboBox<StepRef> elseGotoCombo;

    private final JLabel validationHint;
    private final JLabel retryLabel;
    private final JLabel delayLabel;
    private final JLabel msLabel;
    private final JLabel thenLabel;
    private final JLabel elseLabel;

    private boolean updating = false;

    public StepConditionPanel(Step step) {
        super(new WrapLayout(FlowLayout.LEFT, 4, 2));
        this.step = step;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Themes.lineColor(this)),
                BorderFactory.createEmptyBorder(2, 2, 4, 2)));

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
        gotoCombo.setRenderer(new StepRef.Renderer());
        rebuildGotoCombo(gotoCombo, cond.getGotoTarget());

        retrySpinner = new JSpinner(new SpinnerNumberModel(cond.getRetryCount(), 0, 20, 1));
        delaySpinner = new JSpinner(new SpinnerNumberModel((int) cond.getRetryDelayMs(), 0, 30000, 100));

        elseActionCombo = new JComboBox<>(ConditionFailAction.values());
        elseActionCombo.setSelectedItem(cond.getElseAction());

        elseGotoCombo = new JComboBox<>();
        elseGotoCombo.setRenderer(new StepRef.Renderer());
        rebuildGotoCombo(elseGotoCombo, cond.getElseGotoTarget());

        Runnable applyAndUpdate = () -> {
            if (updating) return;
            updateFieldVisibility();
            String gotoTarget = selectedStepId(gotoCombo);
            String elseGotoTarget = selectedStepId(elseGotoCombo);
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

        JLabel header = new JLabel("Condition: ");
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        add(header);
        add(new JLabel("If"));
        add(typeCombo);
        add(matchModeCombo);
        add(patternField);
        thenLabel = new JLabel("Then");
        add(thenLabel);
        add(actionCombo);
        add(gotoCombo);
        elseLabel = new JLabel(", else");
        add(elseLabel);
        add(elseActionCombo);
        add(elseGotoCombo);
        retryLabel = new JLabel(", retry");
        add(retryLabel);
        add(retrySpinner);
        delayLabel = new JLabel("every");
        add(delayLabel);
        add(delaySpinner);
        msLabel = new JLabel("ms");
        add(msLabel);

        validationHint = new JLabel("  (validation step - action ignored)");
        validationHint.setFont(validationHint.getFont().deriveFont(Font.ITALIC));
        validationHint.setForeground(Themes.disabledForeground(validationHint));
        validationHint.setVisible(false);
        add(validationHint);

        updateFieldVisibility();

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
        rebuildGotoCombo(gotoCombo, currentGotoId);
        rebuildGotoCombo(elseGotoCombo, currentElseGotoId);
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

        thenLabel.setText(isAlways ? "→" : "Then");

        boolean showElse = !isAlways;
        elseLabel.setVisible(showElse);
        elseActionCombo.setVisible(showElse);
        boolean showElseGoto = showElse && elseActionCombo.getSelectedItem() == ConditionFailAction.GOTO_STEP;
        elseGotoCombo.setVisible(showElseGoto);

        revalidate();
        repaint();
    }

    private void rebuildGotoCombo(JComboBox<StepRef> combo, String selectedStepIdOrTitle) {
        boolean wasUpdating = updating;
        updating = true;
        try {
            combo.removeAllItems();
            combo.addItem(StepRef.NONE_GOTO);
            StepRef toSelect = StepRef.NONE_GOTO;
            if (step.getSequence() != null) {
                List<Step> siblings = step.getSequence().getSteps();
                for (int i = 0; i < siblings.size(); i++) {
                    Step s = siblings.get(i);
                    if (s == step) continue;
                    StepRef ref = new StepRef(s, i + 1);
                    combo.addItem(ref);
                    if (selectedStepIdOrTitle != null && !selectedStepIdOrTitle.isEmpty()
                            && (selectedStepIdOrTitle.equals(s.getStepId())
                                || selectedStepIdOrTitle.equalsIgnoreCase(s.getTitle()))) {
                        toSelect = ref;
                    }
                }
            }
            combo.setSelectedItem(toSelect);
        } finally {
            updating = wasUpdating;
        }
    }

    private static String selectedStepId(JComboBox<StepRef> combo) {
        Object sel = combo.getSelectedItem();
        if (sel instanceof StepRef r) return r.getStepId();
        return null;
    }
}

