package com.xreous.stepperng.step.view;

import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.util.view.PostExecutionStepVariableEditor;
import com.xreous.stepperng.util.view.PostExecutionStepVariableRenderer;
import com.xreous.stepperng.variable.PostExecutionStepVariable;
import com.xreous.stepperng.variable.RegexVariable;
import com.xreous.stepperng.variable.VariableManager;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.listener.StepVariableListener;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PostExecutionVariableTable extends JTable {

    private final VariableManager variableManager;
    private Step step;

    public PostExecutionVariableTable(VariableManager variableManager){
        this(variableManager, null);
    }

    public PostExecutionVariableTable(VariableManager variableManager, Step step){
        super();
        this.variableManager = variableManager;
        this.step = step;
        this.setModel(new PostExecutionVariableTableModel(variableManager));
        this.getColumnModel().getColumn(1).setCellRenderer(new PostExecutionStepVariableRenderer());
        this.getColumnModel().getColumn(1).setCellEditor(new PostExecutionStepVariableEditor());
        this.createDefaultTableHeader();

        FontMetrics metrics = this.getFontMetrics(this.getFont());
        int fontHeight = metrics.getHeight();
        this.setRowHeight( fontHeight + 10 );

        this.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        this.getColumnModel().getColumn(0).setPreferredWidth(120);
        this.getColumnModel().getColumn(1).setPreferredWidth(280);
        this.getColumnModel().getColumn(2).setPreferredWidth(180);
        this.getColumnModel().getColumn(3).setPreferredWidth(60);
        this.getColumnModel().getColumn(3).setMaxWidth(80);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { handlePopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handlePopup(e); }
        });
    }

    private void handlePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = rowAtPoint(e.getPoint());
        if (row < 0 || row >= variableManager.getPostExecutionVariables().size()) return;
        setRowSelectionInterval(row, row);
        PostExecutionStepVariable var = variableManager.getPostExecutionVariables().get(row);

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

        if (var instanceof RegexVariable rv && rv.getConditionText() != null && !rv.getConditionText().isEmpty()) {
            JMenuItem copyRegex = new JMenuItem("Copy Regex");
            copyRegex.addActionListener(a -> copyToClipboard(rv.getConditionText()));
            menu.add(copyRegex);
        }

        menu.addSeparator();
        JMenuItem togglePublished = new JMenuItem(var.isPublished() ? "Unpublish" : "Publish");
        togglePublished.addActionListener(a -> {
            var.setPublished(!var.isPublished());
            ((AbstractTableModel) getModel()).fireTableRowsUpdated(row, row);
        });
        menu.add(togglePublished);

        menu.show(this, e.getX(), e.getY());
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    public void setStep(Step step) {
        this.step = step;
    }

    private class PostExecutionVariableTableModel extends AbstractTableModel implements StepVariableListener {

        private final VariableManager variableManager;

        private PostExecutionVariableTableModel(VariableManager variableManager){
            this.variableManager = variableManager;
            this.variableManager.addVariableListener(this);
        }

        @Override
        public Class<?> getColumnClass(int i) {
            if (i == 3) return Boolean.class;
            return String.class;
        }

        @Override
        public String getColumnName(int i) {
            switch(i){
                case 0: return "Identifier";
                case 1: return "Condition";
                case 2: return "Value";
                case 3: return "Published";
                default: return "N/A";
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 || column == 1 || column == 3;
        }

        @Override
        public int getRowCount() {
            return this.variableManager.getPostExecutionVariables().size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public Object getValueAt(int row, int col) {
            PostExecutionStepVariable variable = this.variableManager.getPostExecutionVariables().get(row);
            switch (col){
                case 0: return variable.getIdentifier();
                case 1: return variable;
                case 2: return variable.getValue();
                case 3: return variable.isPublished();
            }

            return "";
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            PostExecutionStepVariable var = this.variableManager.getPostExecutionVariables().get(row);
            switch (col){
                case 0: var.setIdentifier((String) value); break;
                case 1: var.setCondition((String) value); break;
                case 3: var.setPublished((Boolean) value); break;
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
