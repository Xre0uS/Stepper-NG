package com.xreous.stepperng.util.view;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Lightweight line-number gutter for a JTextComponent.
 * Set as the row-header view of the enclosing JScrollPane.
 * Only repaints the visible portion for performance with large documents.
 */
public class LineNumberGutter extends JPanel implements DocumentListener, PropertyChangeListener {

    private final JTextComponent textComponent;
    private final Color lineNumColor;
    private final Color currentLineColor;
    private static final int H_PAD = 8;
    private int cachedDigits = 0;

    public LineNumberGutter(JTextComponent textComponent) {
        this.textComponent = textComponent;

        boolean isDark = isDark();
        lineNumColor = isDark ? new Color(120, 120, 120) : new Color(150, 150, 150);
        currentLineColor = isDark ? new Color(180, 180, 180) : new Color(90, 90, 90);
        Color bg = isDark ? new Color(49, 51, 53) : new Color(243, 243, 243);
        Color border = isDark ? new Color(70, 70, 70) : new Color(215, 215, 215);

        setBackground(bg);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, border));
        setFont(textComponent.getFont());

        textComponent.getDocument().addDocumentListener(this);
        textComponent.addPropertyChangeListener("font", this);
        textComponent.addCaretListener(e -> repaint());
    }

    private static boolean isDark() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        return (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0 < 0.5;
    }

    private int lineCount() {
        return textComponent.getDocument().getDefaultRootElement().getElementCount();
    }

    private int digitCount() {
        return Math.max(String.valueOf(lineCount()).length(), 2);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.charWidth('0') * digitCount() + H_PAD * 2 + 2;
        return new Dimension(w, textComponent.getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            Rectangle clip = g2.getClipBounds();

            int digits = digitCount();
            int digitWidth = fm.charWidth('0') * digits;

            Element root = textComponent.getDocument().getDefaultRootElement();
            int caretLine = root.getElementIndex(textComponent.getCaretPosition());
            int totalLines = root.getElementCount();

            for (int i = 0; i < totalLines; i++) {
                int offset = root.getElement(i).getStartOffset();
                Rectangle r;
                try {
                    var r2d = textComponent.modelToView2D(offset);
                    if (r2d == null) continue;
                    r = r2d.getBounds();
                } catch (BadLocationException e) {
                    continue;
                }

                // Only paint visible lines
                if (r.y + r.height < clip.y) continue;
                if (r.y > clip.y + clip.height) break;

                String num = String.valueOf(i + 1);
                int x = H_PAD + digitWidth - fm.stringWidth(num);
                int y = r.y + ((r.height - fm.getHeight()) / 2) + fm.getAscent();

                g2.setColor(i == caretLine ? currentLineColor : lineNumColor);
                g2.drawString(num, x, y);
            }
        } finally {
            g2.dispose();
        }
    }


    private void docChanged() {
        int d = digitCount();
        if (d != cachedDigits) {
            cachedDigits = d;
            revalidate();
        }
        repaint();
    }

    @Override public void insertUpdate(DocumentEvent e)  { docChanged(); }
    @Override public void removeUpdate(DocumentEvent e)   { docChanged(); }
    @Override public void changedUpdate(DocumentEvent e)   { docChanged(); }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("font".equals(evt.getPropertyName())) {
            setFont(textComponent.getFont());
            revalidate();
            repaint();
        }
    }
}

