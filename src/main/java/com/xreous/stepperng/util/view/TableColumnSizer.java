package com.xreous.stepperng.util.view;

import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.FontMetrics;

/** Derives JTable column widths from FontMetrics + per-column soft character caps. */
public final class TableColumnSizer {

    private TableColumnSizer() {}

    /**
     * Size columns to fit header + row content, clamped by {@code softCapChars}.
     * A cap of {@code 0} means unbounded. Columns past {@code softCapChars.length} are left alone.
     */
    public static void pack(JTable table, int[] softCapChars) {
        if (table == null || table.getColumnCount() == 0) return;
        TableColumnModel cm = table.getColumnModel();
        FontMetrics fm = table.getFontMetrics(table.getFont());
        int emWidth = Math.max(1, fm.charWidth('M'));
        int padding = emWidth * 2; // ~2 chars for cell insets

        int limit = Math.min(cm.getColumnCount(), softCapChars.length);
        for (int col = 0; col < limit; col++) {
            int headerW = fm.stringWidth(String.valueOf(table.getColumnName(col))) + padding;
            int needed = headerW;
            int rows = table.getRowCount();
            for (int r = 0; r < rows; r++) {
                Object v = table.getValueAt(r, col);
                if (v != null && !(v instanceof Boolean)) {
                    needed = Math.max(needed, fm.stringWidth(v.toString()) + padding);
                }
            }
            int cap = softCapChars[col];
            int pref = cap > 0 ? Math.min(needed, emWidth * cap) : needed;
            TableColumn c = cm.getColumn(col);
            c.setMinWidth(Math.min(pref, headerW));
            c.setPreferredWidth(pref);
            if (cap > 0) c.setMaxWidth(Math.max(pref, emWidth * cap));
            else         c.setMaxWidth(Integer.MAX_VALUE);
        }
    }

    /** Hard-cap a single column to fit a max-digit string (e.g., "9999" for row-index columns). */
    public static void fixWidthForDigits(JTable table, int col, int digits) {
        if (table == null || col < 0 || col >= table.getColumnCount()) return;
        FontMetrics fm = table.getFontMetrics(table.getFont());
        int w = fm.stringWidth("9".repeat(Math.max(1, digits))) + fm.charWidth('M') * 2;
        TableColumn c = table.getColumnModel().getColumn(col);
        c.setMinWidth(w);
        c.setPreferredWidth(w);
        c.setMaxWidth(w);
    }
}

