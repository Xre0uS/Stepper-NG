package com.xreous.stepperng.util.variablereplacementstab;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.sequencemanager.SequenceManager;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.util.Utils;
import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.StaticGlobalVariable;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.listener.StepVariableListener;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.util.view.WrappedTextPane;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableReplacementsTab implements ExtensionProvidedHttpRequestEditor {

    private final SequenceManager sequenceManager;
    private final JPanel wrapperPanel;
    private final JScrollPane scrollPane;
    private final JTextPane textArea;
    private final StyledDocument document;
    private final JPanel searchBar;
    private final JTextField searchField;

    private Step step;
    private HttpRequest currentRequest;
    private byte[] rawRequest;
    private int lastSearchIndex = 0;
    private final StepVariableListener variableRefreshListener;

    public VariableReplacementsTab(SequenceManager sequenceManager) {
        this.sequenceManager = sequenceManager;

        this.variableRefreshListener = new StepVariableListener() {
            @Override public void onVariableAdded(StepVariable variable) { scheduleRefresh(); }
            @Override public void onVariableRemoved(StepVariable variable) { scheduleRefresh(); }
            @Override public void onVariableChange(StepVariable variable) { scheduleRefresh(); }
        };
        registerVariableListeners();

        Font editorFont = Utils.getEditorFont();

        this.textArea = new WrappedTextPane();
        this.document = this.textArea.getStyledDocument();
        this.scrollPane = new JScrollPane(this.textArea);
        this.scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        this.scrollPane.setBorder(null);
        this.textArea.setBorder(null);

        Color fgColor = UIManager.getColor("TextPane.foreground");
        Color bgColor = UIManager.getColor("TextPane.background");
        if (fgColor == null) fgColor = UIManager.getColor("text");
        if (bgColor == null) bgColor = UIManager.getColor("control");
        if (fgColor == null) fgColor = Color.BLACK;
        if (bgColor == null) bgColor = Color.WHITE;

        textArea.setBackground(bgColor);
        textArea.setForeground(fgColor);

        boolean isDark = (0.299 * bgColor.getRed() + 0.587 * bgColor.getGreen() + 0.114 * bgColor.getBlue()) / 255.0 < 0.5;

        Style defaultStyle = document.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(defaultStyle, editorFont.getFamily());
        StyleConstants.setFontSize(defaultStyle, editorFont.getSize());
        StyleConstants.setForeground(defaultStyle, fgColor);

        Style highlightedStyle = document.addStyle("highlighted", null);
        StyleConstants.setFontFamily(highlightedStyle, editorFont.getFamily());
        StyleConstants.setFontSize(highlightedStyle, editorFont.getSize());
        StyleConstants.setBold(highlightedStyle, true);
        StyleConstants.setForeground(highlightedStyle, isDark ? new Color(165, 195, 91) : new Color(176, 0, 192));

        textArea.setEditable(false);

        searchBar = new JPanel(new BorderLayout(4, 0));
        searchBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search...");

        JButton prevBtn = new JButton("\u25B2");
        prevBtn.setMargin(new Insets(1, 4, 1, 4));
        prevBtn.setToolTipText("Previous match");
        prevBtn.addActionListener(e -> searchInDirection(false));

        JButton nextBtn = new JButton("\u25BC");
        nextBtn.setMargin(new Insets(1, 4, 1, 4));
        nextBtn.setToolTipText("Next match");
        nextBtn.addActionListener(e -> searchInDirection(true));

        JButton closeBtn = new JButton("\u2715");
        closeBtn.setMargin(new Insets(1, 4, 1, 4));
        closeBtn.setToolTipText("Close search");
        closeBtn.addActionListener(e -> hideSearchBar());

        searchField.addActionListener(e -> searchInDirection(true));

        JPanel searchBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        searchBtns.add(prevBtn);
        searchBtns.add(nextBtn);
        searchBtns.add(closeBtn);

        searchBar.add(new JLabel("Find: "), BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchBtns, BorderLayout.EAST);
        searchBar.setVisible(false);

        wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(scrollPane, BorderLayout.CENTER);
        wrapperPanel.add(searchBar, BorderLayout.SOUTH);

        InputMap im = wrapperPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = wrapperPanel.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "showSearch");
        am.put("showSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSearchBar();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hideSearch");
        am.put("hideSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hideSearchBar();
            }
        });

        InputMap searchIm = searchField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap searchAm = searchField.getActionMap();
        searchIm.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hideSearch");
        searchAm.put("hideSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hideSearchBar();
            }
        });
    }

    private void showSearchBar() {
        searchBar.setVisible(true);
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    private void hideSearchBar() {
        searchBar.setVisible(false);
        textArea.requestFocusInWindow();
        textArea.getHighlighter().removeAllHighlights();
        lastSearchIndex = 0;
    }

    private void searchInDirection(boolean forward) {
        String query = searchField.getText();
        if (query.isEmpty()) return;
        String text;
        try {
            text = document.getText(0, document.getLength());
        } catch (BadLocationException ex) {
            return;
        }
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();

        int foundIndex;
        if (forward) {
            foundIndex = lowerText.indexOf(lowerQuery, lastSearchIndex);
            if (foundIndex < 0) foundIndex = lowerText.indexOf(lowerQuery);
        } else {
            int searchFrom = lastSearchIndex - 1;
            if (searchFrom < 0) searchFrom = lowerText.length() - 1;
            foundIndex = lowerText.lastIndexOf(lowerQuery, searchFrom);
            if (foundIndex < 0) foundIndex = lowerText.lastIndexOf(lowerQuery);
        }

        textArea.getHighlighter().removeAllHighlights();
        if (foundIndex >= 0) {
            try {
                textArea.getHighlighter().addHighlight(foundIndex, foundIndex + query.length(),
                        new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(
                                UIManager.getColor("TextArea.selectionBackground")));
                textArea.setCaretPosition(foundIndex);
                lastSearchIndex = foundIndex + (forward ? query.length() : 0);
            } catch (BadLocationException ex) { }
        }
    }

    @Override
    public String caption() {
        return "Stepper Replacements";
    }

    @Override
    public Component uiComponent() {
        return this.wrapperPanel;
    }

    @Override
    public boolean isEnabledFor(burp.api.montoya.http.message.HttpRequestResponse requestResponse) {
        return true;
    }

    @Override
    public void setRequestResponse(burp.api.montoya.http.message.HttpRequestResponse requestResponse) {
        this.currentRequest = requestResponse.request();
        if (this.currentRequest != null) {
            this.rawRequest = this.currentRequest.toByteArray().getBytes();
            findStep();
            updateMessageWithReplacements(this.rawRequest);
        }
    }

    @Override
    public HttpRequest getRequest() {
        return this.currentRequest;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public Selection selectedData() {
        String selected = this.textArea.getSelectedText();
        if (selected == null) return null;
        return Selection.selection(this.textArea.getSelectionStart(), this.textArea.getSelectionEnd());
    }

    private void registerVariableListeners() {
        for (StepSequence seq : sequenceManager.getSequences()) {
            attachSequenceListeners(seq);
        }
        DynamicGlobalVariableManager dvarManager = Stepper.getDynamicGlobalVariableManager();
        if (dvarManager != null) {
            dvarManager.addVariableListener(variableRefreshListener);
        }
        sequenceManager.addStepSequenceListener(new com.xreous.stepperng.sequencemanager.listener.StepSequenceListener() {
            @Override public void onStepSequenceAdded(StepSequence sequence) { attachSequenceListeners(sequence); }
            @Override public void onStepSequenceRemoved(StepSequence sequence) {}
        });
    }

    private void attachSequenceListeners(StepSequence seq) {
        for (com.xreous.stepperng.step.Step s : seq.getSteps()) {
            s.getVariableManager().addVariableListener(variableRefreshListener);
        }
        seq.addStepListener(new com.xreous.stepperng.step.listener.StepAdapter() {
            @Override
            public void onStepAdded(com.xreous.stepperng.step.Step step) {
                step.getVariableManager().addVariableListener(variableRefreshListener);
            }
        });
    }

    private void scheduleRefresh() {
        SwingUtilities.invokeLater(() -> updateMessageWithReplacements(this.rawRequest));
    }

    private void findStep() {
        if (this.rawRequest == null) return;
        List<StepSequence> stepSequences = sequenceManager.getSequences();
        for (StepSequence stepSequence : stepSequences) {
            for (Step s : stepSequence.getSteps()) {
                byte[] stepReq = s.getRequest();
                if (stepReq != null && java.util.Arrays.equals(this.rawRequest, stepReq)) {
                    this.step = s;
                    return;
                }
            }
        }
        this.step = null;
    }

    private void updateMessageWithReplacements(byte[] content) {
        if (content == null) {
            this.textArea.setText("");
            return;
        }
        HashMap<StepSequence, List<StepVariable>> inSequenceVars;
        HashMap<StepSequence, List<StepVariable>> crossSequenceVars;
        if (this.step != null) {
            // In-sequence: only variables up to this step (for $VAR:name$ replacement)
            inSequenceVars = new HashMap<>();
            inSequenceVars.put(this.step.getSequence(),
                    this.step.getSequence().getRollingVariablesUpToStep(this.step));

            // Cross-sequence: full variable lists from ALL sequences (for $VAR:seq:name$ replacement)
            crossSequenceVars = this.sequenceManager.getRollingVariablesFromAllSequences();
        } else {
            inSequenceVars = new HashMap<>();
            crossSequenceVars = this.sequenceManager.getRollingVariablesFromAllSequences();
        }

        try {
            Stepper.montoya.logging().logToOutput("Stepper-NG [ReplacementsTab] step=" + (this.step != null ? this.step.getTitle() : "null")
                + ", inSeqEntries=" + inSequenceVars.size() + ", crossSeqEntries=" + crossSequenceVars.size());
            for (Map.Entry<StepSequence, List<StepVariable>> e : crossSequenceVars.entrySet()) {
                for (StepVariable v : e.getValue()) {
                    Stepper.montoya.logging().logToOutput("  cross-seq: " + e.getKey().getTitle() + ":" + v.getIdentifier()
                        + " published=" + v.isPublished() + " value=" + (v.getValuePreview() != null ? v.getValuePreview().substring(0, Math.min(30, v.getValuePreview().length())) : "null"));
                }
            }
        } catch (Exception ignored) {}

        String contentString = new String(content);
        try {
            replaceAndHighlight(contentString, inSequenceVars, crossSequenceVars);
        } catch (BadLocationException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            textArea.setText("Something went wrong:\n\n" + sw);
        }
    }

    private void replaceAndHighlight(String content,
                                     HashMap<StepSequence, List<StepVariable>> inSequenceVariables,
                                     HashMap<StepSequence, List<StepVariable>> crossSequenceVariables) throws BadLocationException {
        StringBuffer output;
        String contentToSearch = content;
        ArrayList<Integer[]> highlightRanges = new ArrayList<>();

        // Replace in-sequence $VAR:name$ variables (only variables up to the current step)
        for (Map.Entry<StepSequence, List<StepVariable>> entry : inSequenceVariables.entrySet()) {
            List<StepVariable> variables = entry.getValue();

            for (StepVariable stepVariable : variables) {
                output = new StringBuffer();
                Pattern pattern = StepVariable.createIdentifierPattern(stepVariable);
                String replacement = stepVariable.getValuePreview() != null ? stepVariable.getValuePreview() : "";
                Matcher m = pattern.matcher(contentToSearch);
                int replacementCount = 0;
                replacement = replacement.replaceAll("\\$", "\\\\\\$");
                while (m.find()) {
                    m.appendReplacement(output, replacement);
                    int foundOffset = m.start() + (replacementCount * (replacement.length() - m.group().length()));
                    int foundLength = Math.abs(output.length() - foundOffset);
                    replacementCount++;
                    for (Integer[] range : highlightRanges) {
                        if (range[0] >= foundOffset) {
                            range[0] = range[0] - foundLength + replacement.length();
                        }
                    }
                    highlightRanges.add(new Integer[]{foundOffset, foundLength});
                }
                m.appendTail(output);
                contentToSearch = output.toString();
            }
        }

        // Replace cross-sequence $VAR:seq:name$ variables (full variable lists from all sequences)
        for (Map.Entry<StepSequence, List<StepVariable>> entry : crossSequenceVariables.entrySet()) {
            StepSequence sequence = entry.getKey();
            List<StepVariable> variables = entry.getValue();

            for (StepVariable stepVariable : variables) {
                output = new StringBuffer();
                Pattern pattern = StepVariable.createIdentifierPatternWithSequence(sequence, stepVariable);

                try {
                    Stepper.montoya.logging().logToOutput("  Trying cross-seq pattern: " + pattern.pattern()
                        + " against content contains=" + contentToSearch.contains("$VAR:" + sequence.getTitle() + ":" + stepVariable.getIdentifier() + "$"));
                } catch (Exception ignored) {}

                String replacement = stepVariable.getValuePreview() != null ? stepVariable.getValuePreview() : "";
                Matcher m = pattern.matcher(contentToSearch);
                int replacementCount = 0;
                replacement = replacement.replaceAll("\\$", "\\\\\\$");
                while (m.find()) {
                    m.appendReplacement(output, replacement);
                    int foundOffset = m.start() + (replacementCount * (replacement.length() - m.group().length()));
                    int foundLength = Math.abs(output.length() - foundOffset);
                    replacementCount++;

                    for (Integer[] range : highlightRanges) {
                        if (range[0] >= foundOffset) {
                            range[0] = range[0] - foundLength + replacement.length();
                        }
                    }
                    highlightRanges.add(new Integer[]{foundOffset, foundLength});
                }
                m.appendTail(output);
                contentToSearch = output.toString();
            }
        }

        // Replace $DVAR: variables
        DynamicGlobalVariableManager dvarManager = Stepper.getDynamicGlobalVariableManager();
        if (dvarManager != null) {
            for (DynamicGlobalVariable dvar : dvarManager.getVariables()) {
                output = new StringBuffer();
                Pattern pattern = DynamicGlobalVariable.createDvarPattern(dvar.getIdentifier());
                String replacement = dvar.getValue() != null ? dvar.getValue() : "";
                Matcher m = pattern.matcher(contentToSearch);
                int replacementCount = 0;
                replacement = replacement.replaceAll("\\$", "\\\\\\$");
                while (m.find()) {
                    m.appendReplacement(output, replacement);
                    int foundOffset = m.start() + (replacementCount * (replacement.length() - m.group().length()));
                    int foundLength = Math.abs(output.length() - foundOffset);
                    replacementCount++;

                    for (Integer[] range : highlightRanges) {
                        if (range[0] >= foundOffset) {
                            range[0] = range[0] - foundLength + replacement.length();
                        }
                    }
                    highlightRanges.add(new Integer[]{foundOffset, foundLength});
                }
                m.appendTail(output);
                contentToSearch = output.toString();
            }

            // Replace $GVAR: variables
            for (StaticGlobalVariable svar : dvarManager.getStaticVariables()) {
                output = new StringBuffer();
                Pattern pattern = StaticGlobalVariable.createGvarPattern(svar.getIdentifier());
                String replacement = svar.getValue() != null ? svar.getValue() : "";
                Matcher m = pattern.matcher(contentToSearch);
                int replacementCount = 0;
                replacement = replacement.replaceAll("\\$", "\\\\\\$");
                while (m.find()) {
                    m.appendReplacement(output, replacement);
                    int foundOffset = m.start() + (replacementCount * (replacement.length() - m.group().length()));
                    int foundLength = Math.abs(output.length() - foundOffset);
                    replacementCount++;

                    for (Integer[] range : highlightRanges) {
                        if (range[0] >= foundOffset) {
                            range[0] = range[0] - foundLength + replacement.length();
                        }
                    }
                    highlightRanges.add(new Integer[]{foundOffset, foundLength});
                }
                m.appendTail(output);
                contentToSearch = output.toString();
            }
        }

        output = new StringBuffer();
        output.append(contentToSearch);

        Style highlighted = document.getStyle("highlighted");
        Style defaultStyle = document.getStyle(StyleContext.DEFAULT_STYLE);

        highlightRanges.sort((a, b) -> a[0] - b[0]);

        document.remove(0, document.getLength());
        int currentOffset = 0;
        for (Integer[] highlightRange : highlightRanges) {
            if (highlightRange[0] > currentOffset) {
                document.insertString(document.getLength(), output.substring(currentOffset, highlightRange[0]), defaultStyle);
            }
            currentOffset = highlightRange[0];
            int end = Math.min(currentOffset + highlightRange[1], output.length());
            document.insertString(document.getLength(), output.substring(currentOffset, end), highlighted);
            currentOffset = end;
        }
        if (currentOffset < output.length()) {
            document.insertString(document.getLength(), output.substring(currentOffset), defaultStyle);
        }
    }
}
