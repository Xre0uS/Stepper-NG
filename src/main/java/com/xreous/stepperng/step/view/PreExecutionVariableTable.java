package com.xreous.stepperng.step.view;

import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.util.view.TableColumnSizer;
import com.xreous.stepperng.variable.PreExecutionStepVariable;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.VariableManager;
import com.xreous.stepperng.variable.listener.StepVariableListener;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PreExecutionVariableTable extends JTable {

    private final VariableManager variableManager;
    private Step step;

    public PreExecutionVariableTable(VariableManager variableManager){
        super();
        this.variableManager = variableManager;
        this.setModel(new PreExecutionVariableTableModel(variableManager));
        this.createDefaultTableHeader();

        FontMetrics metrics = this.getFontMetrics(this.getFont());
        int fontHeight = metrics.getHeight();
        this.setRowHeight( fontHeight + 10 );

        this.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Columns: Identifier, Last Value
        TableColumnSizer.pack(this, new int[]{20, 40});

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { handlePopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handlePopup(e); }
        });
    }

    public void setStep(Step step) {
        this.step = step;
    }

    private void handlePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = rowAtPoint(e.getPoint());
        if (row < 0 || row >= variableManager.getPreExecutionVariables().size()) return;
        setRowSelectionInterval(row, row);
        PreExecutionStepVariable var = variableManager.getPreExecutionVariables().get(row);

        JPopupMenu menu = new JPopupMenu();

        String seqTitle = (step != null && step.getSequence() != null) ? step.getSequence().getTitle() : null;
        if (seqTitle != null) {
            String usage = "$VAR:" + seqTitle + ":" + var.getIdentifier() + "$";
            JMenuItem copyRef = new JMenuItem("Copy Variable Reference: " + usage);
            copyRef.addActionListener(a -> copyToClipboard(usage));
            menu.add(copyRef);
        }
        String inSeqUsage = "$VAR:" + var.getIdentifier() + "$";
        JMenuItem copyInSeqRef = new JMenuItem("Copy In-Sequence Reference: " + inSeqUsage);
        copyInSeqRef.addActionListener(a -> copyToClipboard(inSeqUsage));
        menu.add(copyInSeqRef);

        String val = var.getValue();
        if (val != null && !val.isEmpty()) {
            String displayVal = val.length() > 60 ? val.substring(0, 60) + "..." : val;
            JMenuItem copyValue = new JMenuItem("Copy Value: " + displayVal);
            copyValue.addActionListener(a -> copyToClipboard(val));
            menu.add(copyValue);
        }

        JMenuItem copyName = new JMenuItem("Copy Variable Name: " + var.getIdentifier());
        copyName.addActionListener(a -> copyToClipboard(var.getIdentifier()));
        menu.add(copyName);

        menu.show(this, e.getX(), e.getY());
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private class PreExecutionVariableTableModel extends AbstractTableModel implements StepVariableListener {

        private final VariableManager variableManager;

        private PreExecutionVariableTableModel(VariableManager variableManager){
            this.variableManager = variableManager;
            this.variableManager.addVariableListener(this);
        }

        @Override
        public Class<?> getColumnClass(int i) {
            return String.class;
        }

        @Override
        public String getColumnName(int i) {
            switch(i){
                case 0: return "Identifier";
                case 1: return "Last Value";
                default: return "N/A";
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public int getRowCount() {
            return this.variableManager.getPreExecutionVariables().size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int row, int col) {
            PreExecutionStepVariable variable = this.variableManager.getPreExecutionVariables().get(row);
            switch (col){
                case 0: return variable.getIdentifier();
                case 1: return variable.getValue();
            }

            return "";
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            PreExecutionStepVariable var = this.variableManager.getPreExecutionVariables().get(row);
            switch (col){
                case 0: var.setIdentifier((String) value); break;
            }
            this.fireTableRowsUpdated(row, row);
        }

        @Override
        public void onVariableAdded(StepVariable variable) {
            SwingUtilities.invokeLater(this::fireTableDataChanged);
        }

        @Override
        public void onVariableRemoved(StepVariable variable) {
            SwingUtilities.invokeLater(this::fireTableDataChanged);
        }

        @Override
        public void onVariableChange(StepVariable variable) {
            SwingUtilities.invokeLater(this::fireTableDataChanged);
        }
    }
}
