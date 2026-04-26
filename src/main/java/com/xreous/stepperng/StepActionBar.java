package com.xreous.stepperng;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.view.StepPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.function.Function;

/** Toolbar above step editors: run controls, Send-to Repeater/Intruder, inline target editor. */
public final class StepActionBar extends JPanel {

    private final Function<Step, StepPanel> panelLookup;

    private final JButton runStepBtn;
    private final JButton runThroughBtn;
    private final JButton runAllBtn;
    private final JButton sendRepeaterBtn;
    private final JButton sendIntruderBtn;
    private final JTextField targetField;
    private final JCheckBox sslBox;

    private StepSequence currentSequence;
    private Step currentStep;
    private boolean suppressTargetEvents;
    private final Timer targetCommitTimer;

    public StepActionBar(Function<Step, StepPanel> panelLookup) {
        super(new BorderLayout());
        this.panelLookup = panelLookup;

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JPanel target = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));

        this.targetCommitTimer = new Timer(250, e -> commitTargetEdit());
        this.targetCommitTimer.setRepeats(false);

        this.runStepBtn = new JButton("Run step");
        this.runStepBtn.setToolTipText("Execute only the selected step");
        this.runStepBtn.addActionListener(e -> runStep());

        this.runThroughBtn = new JButton("Run through here");
        this.runThroughBtn.setToolTipText("Execute steps from the start up to and including the selected step");
        this.runThroughBtn.addActionListener(e -> runThrough());

        this.runAllBtn = new JButton("Run all");
        this.runAllBtn.setToolTipText("Execute the entire sequence");
        this.runAllBtn.addActionListener(e -> runAll());

        this.sendRepeaterBtn = new JButton("Send to Repeater");
        this.sendRepeaterBtn.setToolTipText("Send the step's request to Repeater");
        this.sendRepeaterBtn.addActionListener(e -> sendTo(true));

        this.sendIntruderBtn = new JButton("Send to Intruder");
        this.sendIntruderBtn.setToolTipText("Send the step's request to Intruder");
        this.sendIntruderBtn.addActionListener(e -> sendTo(false));

        this.targetField = new JTextField(22);
        this.targetField.setToolTipText("host or host:port");
        this.targetField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleTargetCommit(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleTargetCommit(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleTargetCommit(); }
        });
        this.targetField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { commitTargetEdit(); }
        });
        this.targetField.addActionListener(e -> commitTargetEdit());
        this.sslBox = new JCheckBox("HTTPS");
        this.sslBox.addActionListener(e -> commitTargetEdit());

        actions.add(runStepBtn);
        actions.add(runThroughBtn);
        actions.add(runAllBtn);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(new JSeparator(SwingConstants.VERTICAL));
        actions.add(sendRepeaterBtn);
        actions.add(sendIntruderBtn);

        target.add(new JLabel("Target:"));
        target.add(targetField);
        target.add(sslBox);

        add(actions, BorderLayout.WEST);
        add(target, BorderLayout.EAST);

        // Ctrl+Enter inside the target field fires Run step (moves focus back to the tree path).
        registerShortcuts();

        setSelection(null, null);
    }

    private void registerShortcuts() {
        // Focus-scoped: only fires when any descendant of the action bar has keyboard focus.
        InputMap im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = getActionMap();
        int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, mod), "stepper.runStep");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, mod | java.awt.event.InputEvent.SHIFT_DOWN_MASK),
                "stepper.runAll");
        am.put("stepper.runStep", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (runStepBtn.isEnabled()) runStep(); }
        });
        am.put("stepper.runAll", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (runAllBtn.isEnabled()) runAll(); }
        });
    }

    public void setSelection(StepSequence sequence, Step step) {
        this.currentSequence = sequence;
        this.currentStep = step;

        boolean hasSeq = sequence != null;
        boolean hasStep = hasSeq && step != null;

        runStepBtn.setEnabled(hasStep);
        runThroughBtn.setEnabled(hasStep);
        runAllBtn.setEnabled(hasSeq);
        sendRepeaterBtn.setEnabled(hasStep);
        sendIntruderBtn.setEnabled(hasStep);
        targetField.setEnabled(hasStep);
        sslBox.setEnabled(hasStep);

        suppressTargetEvents = true;
        try {
            if (hasStep) {
                String host = step.getHostname() == null ? "" : step.getHostname();
                Integer port = step.getPort();
                String text = (port != null && port > 0) ? host + ":" + port : host;
                targetField.setText(text);
                sslBox.setSelected(step.isSSL());
            } else {
                targetField.setText("");
                sslBox.setSelected(false);
            }
        } finally {
            suppressTargetEvents = false;
        }
    }

    // ----- actions ---------------------------------------------------------

    private void runStep() {
        if (currentSequence == null || currentStep == null) return;
        Step step = currentStep;
        StepSequence seq = currentSequence;
        Thread t = new Thread(() -> {
            try { seq.executeStepBlocking(step); }
            catch (Throwable th) {
                try { Stepper.montoya.logging().logToError("Stepper-NG: run-step worker crashed: " + th); } catch (Exception ignored) {}
            }
        }, "Stepper-NG-RunStep-" + step.getTitle());
        t.setDaemon(true);
        t.start();
    }

    private void runThrough() {
        if (currentSequence == null || currentStep == null) return;
        currentSequence.executeThroughStepAsync(currentStep);
    }

    private void runAll() {
        if (currentSequence == null) return;
        currentSequence.executeAsync();
    }

    private void sendTo(boolean repeater) {
        if (currentSequence == null || currentStep == null) return;
        StepSequence seq = currentSequence;
        Step step = currentStep;

        byte[] requestBytes = liveRequestBytes(seq, step);
        if (requestBytes == null || requestBytes.length == 0) {
            JOptionPane.showMessageDialog(Stepper.suiteFrame(), "Step request is empty.", "Send", JOptionPane.WARNING_MESSAGE);
            return;
        }


        String host = step.getHostname();
        Integer port = step.getPort();
        boolean ssl = step.isSSL();
        if (host == null || host.isEmpty()) {
            JOptionPane.showMessageDialog(Stepper.suiteFrame(), "Step has no target host. Set one in the Target field first.",
                    "Send", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (port == null || port <= 0) port = ssl ? 443 : 80;

        try {
            HttpService svc = HttpService.httpService(host, port, ssl);
            HttpRequest req = HttpRequest.httpRequest(svc, ByteArray.byteArray(requestBytes));
            if (repeater) {
                Stepper.montoya.repeater().sendToRepeater(req, "Stepper: " + step.getTitle());
            } else {
                Stepper.montoya.intruder().sendToIntruder(req);
            }
        } catch (Exception ex) {
            Stepper.montoya.logging().logToError("Stepper-NG: send to " + (repeater ? "Repeater" : "Intruder")
                    + " failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(Stepper.suiteFrame(), "Send failed: " + ex.getMessage(),
                    "Send", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void scheduleTargetCommit() {
        if (suppressTargetEvents) return;
        targetCommitTimer.restart();
    }

    private void commitTargetEdit() {
        targetCommitTimer.stop();
        if (suppressTargetEvents) return;
        if (currentStep == null) return;
        String raw = targetField.getText().trim();
        String host = raw;
        Integer port = null;
        int colon = raw.indexOf(':');
        if (colon >= 0) {
            host = raw.substring(0, colon);
            String portStr = raw.substring(colon + 1).trim();
            if (!portStr.isEmpty()) {
                try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}
            }
        }
        boolean changed = false;
        String prevHost = currentStep.getHostname();
        if (!java.util.Objects.equals(prevHost == null ? "" : prevHost, host)) {
            currentStep.setHostname(host);
            changed = true;
        }
        Integer prevPort = currentStep.getPort();
        // Reset port when colon is present but empty / non-numeric, or absent.
        if (!java.util.Objects.equals(prevPort, port)) {
            currentStep.setPort(port);
            changed = true;
        }
        if (currentStep.isSSL() != sslBox.isSelected()) {
            currentStep.setSSL(sslBox.isSelected());
            changed = true;
        }
        if (changed && currentSequence != null) currentSequence.stepModified(currentStep);
    }

    private byte[] liveRequestBytes(StepSequence seq, Step step) {
        StepPanel panel = panelLookup != null ? panelLookup.apply(step) : null;
        if (panel != null) {
            try {
                byte[] live = panel.getRequestEditor().getMessage();
                if (live != null && live.length > 0) return live;
            } catch (Exception ignored) {}
        }
        return step.getRequest();
    }
}




