package com.xreous.stepperng.util;

import com.xreous.stepperng.util.view.LineNumberGutter;

import javax.swing.*;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Standalone auto-regex dialog that can be opened from any context.
 */
public class AutoRegexDialog {

    public static class Result {
        public final String regex;
        public final String variableName;

        public Result(String regex, String variableName) {
            this.regex = regex;
            this.variableName = variableName;
        }
    }

    /**
     * Show the auto-regex dialog for the given message text.
     *
     * @param parent        parent component for dialog positioning
     * @param messageText   the full request or response text
     * @param title         dialog title
     * @param contentLabel  label hint (e.g., "request" or "response")
     * @param preSelection  optional pre-selected text from Burp's editor (null if none)
     * @param preSelOffset  byte offset of the pre-selection in messageText (-1 if none)
     * @return Result with regex and variable name, or null if cancelled
     */
    public static Result show(Component parent, String messageText, String title,
                              String contentLabel, String preSelection, int preSelOffset) {
        Font editorFont = Utils.getEditorFont();

        Window ancestor = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog;
        if (ancestor instanceof Frame f) {
            dialog = new JDialog(f, title, true);
        } else if (ancestor instanceof Dialog d) {
            dialog = new JDialog(d, title, true);
        } else {
            dialog = new JDialog((Frame) null, title, true);
        }
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setMinimumSize(new Dimension(600, 400));
        int width = Math.min(Math.max(700, (int) (screen.width * 0.55)), screen.width - 100);
        int height = Math.min(Math.max(500, (int) (screen.height * 0.65)), screen.height - 100);
        dialog.setSize(new Dimension(width, height));
        dialog.setLocationRelativeTo(null);

        JTextField nameField = new JTextField(20);

        JTextArea textArea = new JTextArea(messageText);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(false);
        textArea.setFont(editorFont);
        JScrollPane textScroll = new JScrollPane(textArea);
        textScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        textScroll.setRowHeaderView(new LineNumberGutter(textArea));

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
            textArea.getHighlighter().removeAllHighlights();
            if (q.isEmpty()) return;
            String lower = messageText.toLowerCase();
            int idx = lower.indexOf(q.toLowerCase(), searchIdx[0]);
            if (idx < 0) idx = lower.indexOf(q.toLowerCase());
            if (idx >= 0) {
                try {
                    textArea.getHighlighter().addHighlight(idx, idx + q.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(
                                    UIManager.getColor("TextArea.selectionBackground")));
                    textArea.setCaretPosition(idx);
                    searchIdx[0] = idx + q.length();
                } catch (Exception ignored) {}
            }
        };
        Runnable doSearchPrev = () -> {
            String q = searchField.getText();
            textArea.getHighlighter().removeAllHighlights();
            if (q.isEmpty()) return;
            String lower = messageText.toLowerCase();
            int from = searchIdx[0] - q.length() - 1;
            if (from < 0) from = lower.length() - 1;
            int idx = lower.lastIndexOf(q.toLowerCase(), from);
            if (idx < 0) idx = lower.lastIndexOf(q.toLowerCase());
            if (idx >= 0) {
                try {
                    textArea.getHighlighter().addHighlight(idx, idx + q.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(
                                    UIManager.getColor("TextArea.selectionBackground")));
                    textArea.setCaretPosition(idx);
                    searchIdx[0] = idx;
                } catch (Exception ignored) {}
            }
        };
        searchField.addActionListener(e -> doSearch.run());
        searchNext.addActionListener(e -> doSearch.run());
        searchPrev.addActionListener(e -> doSearchPrev.run());
        searchClose.addActionListener(e -> {
            searchBar.setVisible(false);
            textArea.getHighlighter().removeAllHighlights();
        });

        JPanel sBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        sBtns.add(searchPrev);
        sBtns.add(searchNext);
        sBtns.add(searchClose);
        searchBar.add(new JLabel("Find: "), BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(sBtns, BorderLayout.EAST);
        searchBar.setVisible(false);

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.add(textScroll, BorderLayout.CENTER);
        textPanel.add(searchBar, BorderLayout.SOUTH);

        InputMap rim = textPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap ram = textPanel.getActionMap();
        rim.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "showSearch");
        ram.put("showSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchBar.setVisible(true);
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });
        rim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hideSearch");
        ram.put("hideSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchBar.setVisible(false);
                textArea.getHighlighter().removeAllHighlights();
            }
        });

        JTextField regexField = new JTextField(60);
        regexField.setFont(editorFont);

        JLabel statusLabel = new JLabel("Highlight text in the " + contentLabel + " to auto-generate a regex.");
        JLabel capturePreviewLabel = new JLabel(" ");
        capturePreviewLabel.setFont(editorFont);
        capturePreviewLabel.setForeground(new Color(0, 128, 0));

        textArea.addCaretListener(e -> {
            String selected = textArea.getSelectedText();
            if (selected == null || selected.isEmpty()) return;
            int selStart = textArea.getSelectionStart();
            String regex = RegexGenerator.generateRegex(messageText, selected, selStart);
            regexField.setText(regex);
            statusLabel.setText("Regex generated for: " +
                    (selected.length() > 60 ? selected.substring(0, 60) + "..." : selected));
            updateCapturePreview(regex, messageText, capturePreviewLabel);
        });

        regexField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                updateCapturePreview(regexField.getText(), messageText, capturePreviewLabel);
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        JPanel topRow = new JPanel(new BorderLayout(5, 5));
        topRow.add(new JLabel("Variable Name:"), BorderLayout.WEST);
        topRow.add(nameField, BorderLayout.CENTER);

        JPanel midLabel = new JPanel(new BorderLayout());
        midLabel.add(new JLabel("Highlight the value you want to capture in the " + contentLabel + " (Ctrl+F to search):"), BorderLayout.WEST);
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
        JButton copyRegexButton = new JButton("Copy Regex");
        JButton cancelButton = new JButton("Cancel");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(copyRegexButton);
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
        centerPanel.add(textPanel, BorderLayout.CENTER);
        contentPanel.add(centerPanel, BorderLayout.CENTER);
        contentPanel.add(southPanel, BorderLayout.SOUTH);

        final Result[] result = {null};
        okButton.addActionListener(e -> {
            String regex = regexField.getText().trim();
            if (!regex.isEmpty()) {
                result[0] = new Result(regex, nameField.getText().trim());
            }
            dialog.dispose();
        });
        copyRegexButton.addActionListener(e -> {
            String regex = regexField.getText().trim();
            if (!regex.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(regex), null);
                statusLabel.setText("Regex copied to clipboard.");
            }
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.setContentPane(contentPanel);

        if (preSelection != null && !preSelection.isEmpty() && preSelOffset >= 0) {
            SwingUtilities.invokeLater(() -> {
                try {
                    int end = preSelOffset + preSelection.length();
                    if (end <= messageText.length()) {
                        textArea.setSelectionStart(preSelOffset);
                        textArea.setSelectionEnd(end);
                        String regex = RegexGenerator.generateRegex(messageText, preSelection, preSelOffset);
                        regexField.setText(regex);
                        statusLabel.setText("Regex generated for: " +
                                (preSelection.length() > 60 ? preSelection.substring(0, 60) + "..." : preSelection));
                        updateCapturePreview(regex, messageText, capturePreviewLabel);
                    }
                } catch (Exception ignored) {}
            });
        }

        dialog.setVisible(true);
        return result[0];
    }

    private static void updateCapturePreview(String regex, String text, JLabel previewLabel) {
        if (regex == null || regex.trim().isEmpty()) {
            previewLabel.setText(" ");
            return;
        }
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(text);
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
}

