package com.xreous.stepperng.util.view;

import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Runs an action when the user double-clicks a {@link JSplitPane}'s divider; survives L&F swaps. */
public final class SplitPaneDoubleClick {

    private SplitPaneDoubleClick() {}

    public static void install(JSplitPane split, Runnable action) {
        attach(split, action);
        split.addPropertyChangeListener("UI", e -> attach(split, action));
    }

    private static void attach(JSplitPane split, Runnable action) {
        if (!(split.getUI() instanceof BasicSplitPaneUI ui)) return;
        Component divider = ui.getDivider();
        if (divider == null) return;
        for (var l : divider.getMouseListeners()) {
            if (l instanceof Marker) return;
        }
        divider.addMouseListener(new Marker(action));
    }

    private static final class Marker extends MouseAdapter {
        private final Runnable action;
        Marker(Runnable a) { this.action = a; }
        @Override public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                SwingUtilities.invokeLater(action);
            }
        }
    }
}


