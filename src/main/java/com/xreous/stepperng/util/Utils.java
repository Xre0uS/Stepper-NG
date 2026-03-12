package com.xreous.stepperng.util;

import com.xreous.stepperng.Stepper;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

public class Utils {

    private static Font cachedEditorFont = null;

    public static ImageIcon loadImage(String filename, int width, int height){
        ClassLoader cldr = Utils.class.getClassLoader();
        URL imageURLMain = cldr.getResource(filename);

        if(imageURLMain != null) {
            Image scaled = new ImageIcon(imageURLMain).getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            ImageIcon scaledIcon = new ImageIcon(scaled);
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) bufferedImage.getGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(scaledIcon.getImage(), null, null);
            return new ImageIcon(bufferedImage);
        }
        return null;
    }

    private static final int MIN_EDITOR_FONT_SIZE = 12;

    public static Font getEditorFont() {
        if (cachedEditorFont != null) return cachedEditorFont;
        try {
            var editor = Stepper.montoya.userInterface().createHttpRequestEditor();
            Component comp = editor.uiComponent();
            Font found = findMonoTextComponentFont(comp);
            if (found != null) {
                if (found.getSize() < MIN_EDITOR_FONT_SIZE) {
                    found = found.deriveFont((float) MIN_EDITOR_FONT_SIZE);
                }
                cachedEditorFont = found;
                return found;
            }
        } catch (Exception ignored) {}
        cachedEditorFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        return cachedEditorFont;
    }

    // Traverse the component tree for a JTextComponent whose font is monospaced.
    private static Font findMonoTextComponentFont(Component comp) {
        if (comp instanceof javax.swing.text.JTextComponent tc) {
            Font f = tc.getFont();
            if (f != null && f.getSize() >= 8 && looksMonospaced(f)) return f;
        }
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                Font f = findMonoTextComponentFont(child);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static boolean looksMonospaced(Font f) {
        Canvas c = new Canvas();
        FontMetrics fm = c.getFontMetrics(f);
        return fm.charWidth('i') == fm.charWidth('W');
    }
}
