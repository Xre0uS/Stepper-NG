package com.xreous.stepperng.util.view;
import javax.swing.JTree;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** JTree that paints parent→child connector lines (Burp's LAF disables built-in ones). */
public class ConnectorTree extends JTree {
    public ConnectorTree(TreeModel model) {
        super(model);
        putClientProperty("JTree.paintLines", Boolean.FALSE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = Themes.lineColor(this);
            Color solid = new Color(base.getRed(), base.getGreen(), base.getBlue(),
                    Math.min(255, base.getAlpha() * 2));
            g2.setColor(solid);

            int rightIndent = 10;
            if (getUI() instanceof BasicTreeUI bui) rightIndent = Math.max(6, bui.getRightChildIndent());

            int rows = getRowCount();
            for (int r = 0; r < rows; r++) {
                TreePath child = getPathForRow(r);
                if (child == null || child.getPathCount() < 3) continue;
                TreePath parent = child.getParentPath();
                Rectangle cb = getPathBounds(child);
                Rectangle pb = getPathBounds(parent);
                if (cb == null || pb == null) continue;

                TreePath prev = (r > 0) ? getPathForRow(r - 1) : null;
                Rectangle prevBounds = (prev != null) ? getPathBounds(prev) : null;
                boolean prevIsSibling = prev != null
                        && prev.getParentPath() != null
                        && prev.getParentPath().equals(parent);

                int childY = cb.y + cb.height / 2;
                int x = cb.x - rightIndent;
                int xTo = cb.x - 3;

                int vStart = prevIsSibling && prevBounds != null
                        ? prevBounds.y + prevBounds.height / 2
                        : pb.y + pb.height;
                if (vStart < childY) g2.drawLine(x, vStart, x, childY);
                g2.drawLine(x, childY, xTo, childY);
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public String convertValueToText(Object value, boolean selected, boolean expanded,
                                     boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode n && n.getUserObject() != null) {
            return n.getUserObject().toString();
        }
        return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
    }
}
