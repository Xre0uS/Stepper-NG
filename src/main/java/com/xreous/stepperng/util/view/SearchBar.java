package com.xreous.stepperng.util.view;
import javax.swing.*;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
/**
 * Reusable Ctrl+F-style find bar for a host {@link JTextComponent}.
 */
public final class SearchBar {
    public interface TextSource { String text(); }
    private final JTextComponent target;
    private final TextSource source;
    private final JPanel bar;
    private final JTextField field = new JTextField();
    private int lastIndex = 0;
    public SearchBar(JTextComponent target) {
        this(target, () -> {
            try { return target.getText(); } catch (Exception e) { return ""; }
        });
    }
    public SearchBar(JTextComponent target, TextSource source) {
        this.target = target;
        this.source = source;
        this.bar = new JPanel(new BorderLayout(4, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        field.putClientProperty("JTextField.placeholderText", "Search...");
        JButton prev = smallButton("\u25B2", "Previous match");
        JButton next = smallButton("\u25BC", "Next match");
        JButton close = smallButton("\u2715", "Close search");
        prev.addActionListener(e -> search(false));
        next.addActionListener(e -> search(true));
        close.addActionListener(e -> hide());
        field.addActionListener(e -> search(true));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        btns.add(prev); btns.add(next); btns.add(close);
        bar.add(new JLabel("Find: "), BorderLayout.WEST);
        bar.add(field, BorderLayout.CENTER);
        bar.add(btns, BorderLayout.EAST);
        bar.setVisible(false);
    }
    public JPanel component() { return bar; }
    public void installShortcuts(JComponent wrapper) {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        wrapper.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask), "stepper.search.show");
        wrapper.getActionMap().put("stepper.search.show", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { show(); }
        });
        wrapper.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "stepper.search.hide");
        wrapper.getActionMap().put("stepper.search.hide", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { hide(); }
        });
        field.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "stepper.search.hide");
        field.getActionMap().put("stepper.search.hide", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { hide(); }
        });
    }
    public void show() {
        bar.setVisible(true);
        field.requestFocusInWindow();
        field.selectAll();
    }
    public void hide() {
        bar.setVisible(false);
        target.getHighlighter().removeAllHighlights();
        lastIndex = 0;
        try { target.requestFocusInWindow(); } catch (Exception ignored) {}
    }
    private void search(boolean forward) {
        String query = field.getText();
        if (query.isEmpty()) return;
        String text = source.text();
        if (text == null) return;
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int found;
        if (forward) {
            found = lowerText.indexOf(lowerQuery, lastIndex);
            if (found < 0) found = lowerText.indexOf(lowerQuery);
        } else {
            int from = Math.max(lastIndex - 1, 0);
            found = lowerText.lastIndexOf(lowerQuery, from);
            if (found < 0) found = lowerText.lastIndexOf(lowerQuery);
        }
        target.getHighlighter().removeAllHighlights();
        if (found >= 0) {
            try {
                Color hl = UIManager.getColor("TextArea.selectionBackground");
                if (hl == null) hl = UIManager.getColor("TextPane.selectionBackground");
                target.getHighlighter().addHighlight(found, found + query.length(),
                        new DefaultHighlighter.DefaultHighlightPainter(hl));
                target.setCaretPosition(found);
                lastIndex = forward ? (found + query.length()) : found;
            } catch (Exception ignored) {}
        }
    }
    private static JButton smallButton(String label, String tooltip) {
        JButton b = new JButton(label);
        b.setMargin(new Insets(1, 4, 1, 4));
        b.setToolTipText(tooltip);
        return b;
    }
}
