package com.xreous.stepperng.util.view;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/** FlowLayout variant that reports preferred size accounting for wrapped rows. */
public class WrapLayout extends FlowLayout {
    public WrapLayout() { super(); }
    public WrapLayout(int align) { super(align); }
    public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    @Override public Dimension preferredLayoutSize(Container target) { return layoutSize(target, true); }

    @Override public Dimension minimumLayoutSize(Container target) {
        Dimension min = layoutSize(target, false);
        min.width -= (getHgap() + 1);
        return min;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Container c = target;
            int targetWidth = c.getSize().width;
            while (targetWidth == 0 && c.getParent() != null) { c = c.getParent(); targetWidth = c.getSize().width; }
            if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

            int hgap = getHgap(), vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontal = insets.left + insets.right + hgap * 2;
            int maxWidth = targetWidth - horizontal;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0, rowHeight = 0;
            for (int i = 0; i < target.getComponentCount(); i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0; rowHeight = 0;
                }
                if (rowWidth != 0) rowWidth += hgap;
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);
            dim.width += horizontal;
            dim.height += insets.top + insets.bottom + vgap * 2;

            Container scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scroll != null && target.isValid()) dim.width -= (hgap + 1);
            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) dim.height += getVgap();
        dim.height += rowHeight;
    }
}

