package com.xreous.stepperng.step.view;

import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.util.RegexGenerator;
import com.xreous.stepperng.util.Utils;
import com.xreous.stepperng.util.dialog.VariableCreationDialog;
import com.xreous.stepperng.variable.RegexVariable;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.VariableManager;

import javax.swing.*;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class PostExecVariablePanel extends VariablePanel {

    private Step step;

    public PostExecVariablePanel(VariableManager variableManager, Step step){
        super("Post-Execution Variables", variableManager);
        this.step = step;
        // Table is created by super() before step is assigned, so set it now
        if (this.variableTable instanceof PostExecutionVariableTable postTable) {
            postTable.setStep(step);
        }
    }

    @Override
    void createVariableTable() {
        this.variableTable = new PostExecutionVariableTable(this.variableManager);
    }

    @Override
    JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new GridLayout(1, 0));

        JButton addVariableButton = new JButton("Add Variable");
        addVariableButton.addActionListener(e -> handleAddVariableEvent());

        JButton autoRegexButton = new JButton("Auto-Regex");
        autoRegexButton.setToolTipText("Highlight text in the response below to auto-generate a regex variable");
        autoRegexButton.addActionListener(e -> handleAutoRegexEvent());

        JButton deleteButton = new JButton("Delete Selected");
        deleteButton.addActionListener(e -> handleDeleteVariableEvent());

        controlPanel.add(addVariableButton);
        controlPanel.add(autoRegexButton);
        controlPanel.add(deleteButton);
        return controlPanel;
    }

    @Override
    void handleAddVariableEvent() {
        VariableCreationDialog dialog = new VariableCreationDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "New Variable", VariableCreationDialog.VariableType.REGEX);
        StepVariable variable = dialog.run();
        if(variable != null) {
            this.variableManager.addVariable(variable);
        }
    }

    private void handleAutoRegexEvent() {
        byte[] responseBytes = step.getResponse();
        if (responseBytes == null || responseBytes.length == 0) {
            JOptionPane.showMessageDialog(this, "No response data yet. Execute the step first.",
                    "No Response", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String responseText = new String(responseBytes);
        Font editorFont = Utils.getEditorFont();

        JDialog dialog = new JDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                "Auto-Generate Regex Variable", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(600, 400));
        dialog.setSize(new Dimension(900, 650));
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));

        JTextField nameField = new JTextField(20);
        JTextArea responseArea = new JTextArea(responseText);
        responseArea.setEditable(false);
        responseArea.setLineWrap(false);
        responseArea.setFont(editorFont);
        JScrollPane responseScroll = new JScrollPane(responseArea);

        JPanel searchBar = new JPanel(new BorderLayout(4, 0));
        searchBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        JTextField searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search...");
        JButton searchPrev = new JButton("\u25B2");
        searchPrev.setMargin(new Insets(1, 4, 1, 4));
        JButton searchNext = new JButton("\u25BC");
        searchNext.setMargin(new Insets(1, 4, 1, 4));
        JButton searchClose = new JButton("\u2715");
        searchClose.setMargin(new Insets(1, 4, 1, 4));

        final int[] searchIdx = {0};
        Runnable doSearch = () -> {
            String q = searchField.getText();
            responseArea.getHighlighter().removeAllHighlights();
            if (q.isEmpty()) return;
            String lower = responseText.toLowerCase();
            int idx = lower.indexOf(q.toLowerCase(), searchIdx[0]);
            if (idx < 0) idx = lower.indexOf(q.toLowerCase());
            if (idx >= 0) {
                try {
                    responseArea.getHighlighter().addHighlight(idx, idx + q.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(
                                    UIManager.getColor("TextArea.selectionBackground")));
                    responseArea.setCaretPosition(idx);
                    searchIdx[0] = idx + q.length();
                } catch (Exception ignored) {}
            }
        };
        Runnable doSearchPrev = () -> {
            String q = searchField.getText();
            responseArea.getHighlighter().removeAllHighlights();
            if (q.isEmpty()) return;
            String lower = responseText.toLowerCase();
            int from = searchIdx[0] - q.length() - 1;
            if (from < 0) from = lower.length() - 1;
            int idx = lower.lastIndexOf(q.toLowerCase(), from);
            if (idx < 0) idx = lower.lastIndexOf(q.toLowerCase());
            if (idx >= 0) {
                try {
                    responseArea.getHighlighter().addHighlight(idx, idx + q.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(
                                    UIManager.getColor("TextArea.selectionBackground")));
                    responseArea.setCaretPosition(idx);
                    searchIdx[0] = idx;
                } catch (Exception ignored) {}
            }
        };
        searchField.addActionListener(e -> doSearch.run());
        searchNext.addActionListener(e -> doSearch.run());
        searchPrev.addActionListener(e -> doSearchPrev.run());
        searchClose.addActionListener(e -> {
            searchBar.setVisible(false);
            responseArea.getHighlighter().removeAllHighlights();
        });

        JPanel sBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        sBtns.add(searchPrev);
        sBtns.add(searchNext);
        sBtns.add(searchClose);
        searchBar.add(new JLabel("Find: "), BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(sBtns, BorderLayout.EAST);
        searchBar.setVisible(false);

        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.add(responseScroll, BorderLayout.CENTER);
        responsePanel.add(searchBar, BorderLayout.SOUTH);

        InputMap rim = responsePanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap ram = responsePanel.getActionMap();
        rim.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "showSearch");
        ram.put("showSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                searchBar.setVisible(true);
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });
        rim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hideSearch");
        ram.put("hideSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                searchBar.setVisible(false);
                responseArea.getHighlighter().removeAllHighlights();
            }
        });

        JTextField regexField = new JTextField(60);
        regexField.setFont(editorFont);

        JLabel statusLabel = new JLabel("Highlight text in the response to auto-generate a regex.");
        JLabel capturePreviewLabel = new JLabel(" ");
        capturePreviewLabel.setFont(editorFont);
        capturePreviewLabel.setForeground(new Color(0, 128, 0));

        responseArea.addCaretListener(e -> {
            String selected = responseArea.getSelectedText();
            if (selected == null || selected.isEmpty()) return;
            int selStart = responseArea.getSelectionStart();
            String regex = RegexGenerator.generateRegex(responseText, selected, selStart);
            regexField.setText(regex);
            statusLabel.setText("Regex generated for: " +
                    (selected.length() > 60 ? selected.substring(0, 60) + "..." : selected));
            updateCapturePreview(regex, responseText, capturePreviewLabel);
        });

        regexField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() { updateCapturePreview(regexField.getText(), responseText, capturePreviewLabel); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        JPanel topRow = new JPanel(new BorderLayout(5, 5));
        topRow.add(new JLabel("Variable Name:"), BorderLayout.WEST);
        topRow.add(nameField, BorderLayout.CENTER);

        JPanel midLabel = new JPanel(new BorderLayout());
        midLabel.add(new JLabel("Highlight the value you want to capture in the response (Ctrl+F to search):"), BorderLayout.WEST);
        midLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));

        JPanel botPanel = new JPanel(new GridLayout(3, 1, 2, 2));
        JPanel regexRow = new JPanel(new BorderLayout(5, 0));
        regexRow.add(new JLabel("Regex: "), BorderLayout.WEST);
        regexRow.add(regexField, BorderLayout.CENTER);
        JPanel previewRow = new JPanel(new BorderLayout(5, 0));
        previewRow.add(new JLabel("Captured: "), BorderLayout.WEST);
        previewRow.add(capturePreviewLabel, BorderLayout.CENTER);
        botPanel.add(regexRow);
        botPanel.add(previewRow);
        botPanel.add(statusLabel);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(botPanel, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        contentPanel.add(topRow, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(midLabel, BorderLayout.NORTH);
        centerPanel.add(responsePanel, BorderLayout.CENTER);
        contentPanel.add(centerPanel, BorderLayout.CENTER);
        contentPanel.add(southPanel, BorderLayout.SOUTH);

        final boolean[] accepted = {false};
        okButton.addActionListener(e -> { accepted[0] = true; dialog.dispose(); });
        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.setContentPane(contentPanel);
        dialog.setVisible(true);

        if (accepted[0] && !regexField.getText().trim().isEmpty()) {
            String varName = nameField.getText().trim();
            if (varName.isEmpty()) varName = "auto_" + System.currentTimeMillis();
            RegexVariable var = new RegexVariable(varName);
            this.variableManager.addVariable(var);
            var.setCondition(regexField.getText().trim());
        }
    }

    private void updateCapturePreview(String regex, String responseText, JLabel previewLabel) {
        if (regex == null || regex.trim().isEmpty()) {
            previewLabel.setText(" ");
            return;
        }
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(responseText);
            if (m.find()) {
                String captured = m.groupCount() > 0 ? m.group(1) : m.group();
                if (captured != null) {
                    String display = captured.length() > 120 ? captured.substring(0, 120) + "..." : captured;
                    display = display.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
                    previewLabel.setText(display);
                    previewLabel.setForeground(new Color(0, 128, 0));
                } else {
                    previewLabel.setText("(null)");
                    previewLabel.setForeground(Color.RED);
                }
            } else {
                previewLabel.setText("No match found");
                previewLabel.setForeground(Color.RED);
            }
        } catch (java.util.regex.PatternSyntaxException ex) {
            previewLabel.setText("Invalid regex: " + ex.getDescription());
            previewLabel.setForeground(Color.RED);
        }
    }

    @Override
    void handleDeleteVariableEvent() {
        if(this.variableTable.getSelectedRow() >= 0) {
            StepVariable variable = this.variableManager.getPostExecutionVariables().get(this.variableTable.getSelectedRow());
            this.variableManager.removeVariable(variable);
        }
    }
}
