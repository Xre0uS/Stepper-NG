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

    public static Font getEditorFont() {
        if (cachedEditorFont != null) return cachedEditorFont;
        try {
            var editor = Stepper.montoya.userInterface().createHttpRequestEditor();
            Component comp = editor.uiComponent();
            Font found = findTextComponentFont(comp);
            if (found != null) {
                cachedEditorFont = found;
                return found;
            }
        } catch (Exception ignored) {}
        cachedEditorFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        return cachedEditorFont;
    }

    private static Font findTextComponentFont(Component comp) {
        if (comp instanceof javax.swing.text.JTextComponent tc) {
            Font f = tc.getFont();
            if (f != null && f.getSize() >= 8) return f;
        }
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                Font f = findTextComponentFont(child);
                if (f != null) return f;
            }
        }
        return null;
    }
}
