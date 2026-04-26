package com.xreous.stepperng.variable.view;
import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.StaticGlobalVariable;
import com.xreous.stepperng.variable.listener.StepVariableListener;
import com.xreous.stepperng.variable.StepVariable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
public class DynamicGlobalVariablesPanel extends JPanel {
    private final DynamicGlobalVariableManager manager;
    private final StaticVarTableModel staticTableModel;
    private final DynamicVarTableModel dynamicTableModel;
    private final JTable staticTable;
    private final JTable dynamicTable;
    private final StaticDetailPanel staticDetail;
    private final DynamicDetailPanel dynamicDetail;
    public DynamicGlobalVariablesPanel(DynamicGlobalVariableManager manager) {
        super(new BorderLayout());
        this.manager = manager;
        this.staticTableModel = new StaticVarTableModel();
        this.staticTable = new JTable(staticTableModel);
        staticTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        staticTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        staticTable.getColumnModel().getColumn(1).setPreferredWidth(400);
        staticTable.setRowHeight(staticTable.getFontMetrics(staticTable.getFont()).getHeight() + 8);
        staticTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { handleStaticPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { handleStaticPopup(e); }
        });
        this.staticDetail = new StaticDetailPanel();
        staticTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = staticTable.getSelectedRow();
            staticDetail.bind(row >= 0 && row < manager.getStaticVariables().size()
                    ? manager.getStaticVariables().get(row) : null);
        });
        JPanel staticButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addStaticBtn = new JButton("Add");
        addStaticBtn.addActionListener(e -> {
            StaticGlobalVariable v = new StaticGlobalVariable("new_var", "");
            manager.addStaticVariable(v);
            int row = manager.getStaticVariables().indexOf(v);
            if (row >= 0) staticTable.setRowSelectionInterval(row, row);
            staticDetail.focusName();
            com.xreous.stepperng.util.DuplicateNameWarning.checkStaticGlobalName(this, v);
        });
        JButton removeStaticBtn = new JButton("Remove");
        removeStaticBtn.addActionListener(e -> removeSelectedStatic());
        staticButtons.add(addStaticBtn);
        staticButtons.add(removeStaticBtn);
        JPanel staticPanel = new JPanel(new BorderLayout());
        JLabel staticLabel = new JLabel("  Static Global Variables — $GVAR:name$ — manual key/value, set inline below.");
        staticLabel.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
        staticPanel.add(staticLabel, BorderLayout.NORTH);
        JSplitPane staticSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(staticTable), staticDetail);
        staticSplit.setResizeWeight(0.6);
        staticSplit.setContinuousLayout(true);
        staticPanel.add(staticSplit, BorderLayout.CENTER);
        staticPanel.add(staticButtons, BorderLayout.SOUTH);
        this.dynamicTableModel = new DynamicVarTableModel();
        this.dynamicTable = new JTable(dynamicTableModel);
        dynamicTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dynamicTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        dynamicTable.getColumnModel().getColumn(1).setPreferredWidth(280);
        dynamicTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        dynamicTable.getColumnModel().getColumn(3).setPreferredWidth(35);
        dynamicTable.getColumnModel().getColumn(3).setMaxWidth(45);
        dynamicTable.getColumnModel().getColumn(4).setPreferredWidth(200);
        dynamicTable.setRowHeight(dynamicTable.getFontMetrics(dynamicTable.getFont()).getHeight() + 8);
        dynamicTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { handleDynamicPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { handleDynamicPopup(e); }
        });
        this.dynamicDetail = new DynamicDetailPanel();
        dynamicTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = dynamicTable.getSelectedRow();
            dynamicDetail.bind(row >= 0 && row < manager.getVariables().size()
                    ? manager.getVariables().get(row) : null);
        });
        JPanel dynamicButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addDynBtn = new JButton("Add");
        addDynBtn.addActionListener(e -> {
            DynamicGlobalVariable v = new DynamicGlobalVariable("new_var", "", null, false);
            manager.addVariable(v);
            int row = manager.getVariables().indexOf(v);
            if (row >= 0) dynamicTable.setRowSelectionInterval(row, row);
            dynamicDetail.focusName();
            com.xreous.stepperng.util.DuplicateNameWarning.checkDynamicGlobalName(this, v);
        });
        JButton removeDynBtn = new JButton("Remove");
        removeDynBtn.addActionListener(e -> removeSelectedDynamic());
        dynamicButtons.add(addDynBtn);
        dynamicButtons.add(removeDynBtn);
        JPanel dynamicPanel = new JPanel(new BorderLayout());
        JLabel dynamicLabel = new JLabel("  Dynamic Global Variables — $DVAR:name$ — auto-extracts from HTTP responses (optionally requests) via regex.");
        dynamicLabel.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
        dynamicPanel.add(dynamicLabel, BorderLayout.NORTH);
        JSplitPane dynamicSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(dynamicTable), dynamicDetail);
        dynamicSplit.setResizeWeight(0.6);
        dynamicSplit.setContinuousLayout(true);
        dynamicPanel.add(dynamicSplit, BorderLayout.CENTER);
        dynamicPanel.add(dynamicButtons, BorderLayout.SOUTH);
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, staticPanel, dynamicPanel);
        splitPane.setResizeWeight(0.4);
        this.add(splitPane, BorderLayout.CENTER);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(() -> {
                    if (splitPane.getHeight() > 0) splitPane.setDividerLocation(0.4);
                    if (staticSplit.getWidth() > 0)  staticSplit.setDividerLocation(0.55);
                    if (dynamicSplit.getWidth() > 0) dynamicSplit.setDividerLocation(0.55);
                });
            }
        });
        manager.addVariableListener(new StepVariableListener() {
            @Override public void onVariableAdded(StepVariable variable) { refreshTables(); }
            @Override public void onVariableRemoved(StepVariable variable) { refreshTables(); }
            @Override public void onVariableChange(StepVariable variable) { refreshTables(); }
        });
    }
    private void handleStaticPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = staticTable.rowAtPoint(e.getPoint());
        if (row < 0 || row >= manager.getStaticVariables().size()) return;
        staticTable.setRowSelectionInterval(row, row);
        StaticGlobalVariable var = manager.getStaticVariables().get(row);
        JPopupMenu menu = new JPopupMenu();
        String usage = StaticGlobalVariable.createGvarString(var.getIdentifier());
        addCopy(menu, "Copy Variable Reference: " + usage, usage);
        String val = var.getValue();
        if (val != null && !val.isEmpty()) {
            String displayVal = val.length() > 60 ? val.substring(0, 60) + "..." : val;
            addCopy(menu, "Copy Value: " + displayVal, val);
        }
        addCopy(menu, "Copy Variable Name: " + var.getIdentifier(), var.getIdentifier());
        menu.addSeparator();
        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(a -> removeSelectedStatic());
        menu.add(removeItem);
        menu.show(staticTable, e.getX(), e.getY());
    }
    private void handleDynamicPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = dynamicTable.rowAtPoint(e.getPoint());
        if (row < 0 || row >= manager.getVariables().size()) return;
        dynamicTable.setRowSelectionInterval(row, row);
        DynamicGlobalVariable var = manager.getVariables().get(row);
        JPopupMenu menu = new JPopupMenu();
        String usage = DynamicGlobalVariable.createDvarString(var.getIdentifier());
        addCopy(menu, "Copy Variable Reference: " + usage, usage);
        String val = var.getValue();
        if (val != null && !val.isEmpty()) {
            String displayVal = val.length() > 60 ? val.substring(0, 60) + "..." : val;
            addCopy(menu, "Copy Value: " + displayVal, val);
        }
        addCopy(menu, "Copy Variable Name: " + var.getIdentifier(), var.getIdentifier());
        if (var.getRegexString() != null && !var.getRegexString().isEmpty()) {
            addCopy(menu, "Copy Regex", var.getRegexString());
        }
        menu.addSeparator();
        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(a -> removeSelectedDynamic());
        menu.add(removeItem);
        menu.show(dynamicTable, e.getX(), e.getY());
    }
    private static void addCopy(JPopupMenu menu, String label, String text) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(a -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null));
        menu.add(item);
    }
    private void refreshTables() {
        SwingUtilities.invokeLater(() -> {
            int sRow = staticTable.getSelectedRow();
            int dRow = dynamicTable.getSelectedRow();
            staticTableModel.fireTableDataChanged();
            dynamicTableModel.fireTableDataChanged();
            if (sRow >= 0 && sRow < staticTable.getRowCount()) staticTable.setRowSelectionInterval(sRow, sRow);
            if (dRow >= 0 && dRow < dynamicTable.getRowCount()) dynamicTable.setRowSelectionInterval(dRow, dRow);
        });
    }
    private void removeSelectedStatic() {
        int row = staticTable.getSelectedRow();
        if (row < 0 || row >= manager.getStaticVariables().size()) return;
        manager.removeStaticVariable(manager.getStaticVariables().get(row));
        staticDetail.bind(null);
    }
    private void removeSelectedDynamic() {
        int row = dynamicTable.getSelectedRow();
        if (row < 0 || row >= manager.getVariables().size()) return;
        manager.removeVariable(manager.getVariables().get(row));
        dynamicDetail.bind(null);
    }
    private final class StaticDetailPanel extends JPanel {
        private final JTextField nameField = new JTextField(20);
        private final JTextField valueField = new JTextField(40);
        private StaticGlobalVariable bound;
        private boolean updating;
        StaticDetailPanel() {
            super(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(2, 4, 2, 4);
            g.anchor = GridBagConstraints.WEST;
            g.gridx = 0; g.gridy = 0; add(new JLabel("Name:"), g);
            g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; add(nameField, g);
            g.gridx = 0; g.gridy = 1; g.fill = GridBagConstraints.NONE; g.weightx = 0; add(new JLabel("Value:"), g);
            g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; add(valueField, g);
            g.gridx = 0; g.gridy = 2; g.gridwidth = 2; g.weighty = 1; g.fill = GridBagConstraints.BOTH;
            add(Box.createGlue(), g);
            attach(nameField, () -> { if (bound != null) bound.setIdentifier(nameField.getText().trim()); });
            attach(valueField, () -> { if (bound != null) bound.setValue(valueField.getText()); });
            nameField.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) {
                    com.xreous.stepperng.util.DuplicateNameWarning.checkStaticGlobalName(StaticDetailPanel.this, bound);
                }
            });
            bind(null);
        }
        void bind(StaticGlobalVariable v) {
            bound = v;
            updating = true;
            try {
                nameField.setText(v != null ? v.getIdentifier() : "");
                valueField.setText(v != null && v.getValue() != null ? v.getValue() : "");
                nameField.setEnabled(v != null);
                valueField.setEnabled(v != null);
            } finally { updating = false; }
        }
        void focusName() { SwingUtilities.invokeLater(() -> { nameField.selectAll(); nameField.requestFocusInWindow(); }); }
        private void attach(JTextField field, Runnable apply) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { commit(); }
                @Override public void removeUpdate(DocumentEvent e) { commit(); }
                @Override public void changedUpdate(DocumentEvent e) { commit(); }
                private void commit() {
                    if (updating || bound == null) return;
                    apply.run();
                    manager.notifyVariableChanged(bound);
                }
            });
        }
    }
    private final class DynamicDetailPanel extends JPanel {
        private final JTextField nameField = new JTextField(20);
        private final JTextField regexField = new JTextField(40);
        private final JTextField hostField = new JTextField(20);
        private final JCheckBox captureRequests = new JCheckBox("Also capture from requests");
        private final JLabel valueLabel = new JLabel(" ");
        private DynamicGlobalVariable bound;
        private boolean updating;
        DynamicDetailPanel() {
            super(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(2, 4, 2, 4);
            g.anchor = GridBagConstraints.WEST;
            g.gridx = 0; g.gridy = 0; add(new JLabel("Name:"), g);
            g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; add(nameField, g);
            g.gridx = 0; g.gridy = 1; g.fill = GridBagConstraints.NONE; g.weightx = 0; add(new JLabel("Regex (group 1):"), g);
            g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; add(regexField, g);
            g.gridx = 0; g.gridy = 2; g.fill = GridBagConstraints.NONE; g.weightx = 0; add(new JLabel("Host filter (regex, optional):"), g);
            g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; add(hostField, g);
            g.gridx = 0; g.gridy = 3; g.gridwidth = 2; add(captureRequests, g);
            g.gridy = 4; add(new JLabel("Current value:"), g);
            g.gridy = 5; valueLabel.setFont(valueLabel.getFont().deriveFont(Font.ITALIC));
            add(valueLabel, g);
            g.gridy = 6; g.weighty = 1; g.fill = GridBagConstraints.BOTH;
            add(Box.createGlue(), g);
            attach(nameField, () -> { if (bound != null) bound.setIdentifier(nameField.getText().trim()); });
            attach(regexField, () -> { if (bound != null) bound.setRegex(regexField.getText().trim()); });
            attach(hostField, () -> {
                if (bound != null) bound.setHostFilter(hostField.getText().trim().isEmpty() ? null : hostField.getText().trim());
            });
            captureRequests.addActionListener(e -> {
                if (updating || bound == null) return;
                bound.setCaptureFromRequests(captureRequests.isSelected());
                manager.notifyVariableChanged(bound);
            });
            nameField.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) {
                    com.xreous.stepperng.util.DuplicateNameWarning.checkDynamicGlobalName(DynamicDetailPanel.this, bound);
                }
            });
            bind(null);
        }
        void bind(DynamicGlobalVariable v) {
            bound = v;
            updating = true;
            try {
                nameField.setText(v != null ? v.getIdentifier() : "");
                regexField.setText(v != null && v.getRegexString() != null ? v.getRegexString() : "");
                hostField.setText(v != null && v.getHostFilter() != null ? v.getHostFilter() : "");
                captureRequests.setSelected(v != null && v.isCaptureFromRequests());
                String val = v != null ? v.getValue() : null;
                valueLabel.setText(val != null && !val.isEmpty() ? val : "(no match yet)");
                boolean en = v != null;
                nameField.setEnabled(en); regexField.setEnabled(en);
                hostField.setEnabled(en); captureRequests.setEnabled(en);
            } finally { updating = false; }
        }
        void focusName() { SwingUtilities.invokeLater(() -> { nameField.selectAll(); nameField.requestFocusInWindow(); }); }
        private void attach(JTextField field, Runnable apply) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { commit(); }
                @Override public void removeUpdate(DocumentEvent e) { commit(); }
                @Override public void changedUpdate(DocumentEvent e) { commit(); }
                private void commit() {
                    if (updating || bound == null) return;
                    apply.run();
                    manager.notifyVariableChanged(bound);
                }
            });
        }
    }
    private class StaticVarTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Name ($GVAR:name$)", "Value"};
        @Override public int getRowCount() { return manager.getStaticVariables().size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public Object getValueAt(int row, int col) {
            StaticGlobalVariable var = manager.getStaticVariables().get(row);
            return switch (col) {
                case 0 -> var.getIdentifier();
                case 1 -> var.getValue() != null ? var.getValue() : "";
                default -> "";
            };
        }
    }
    private class DynamicVarTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Name ($DVAR:name$)", "Regex", "Host Filter", "Req", "Current Value"};
        @Override public int getRowCount() { return manager.getVariables().size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public Object getValueAt(int row, int col) {
            DynamicGlobalVariable var = manager.getVariables().get(row);
            return switch (col) {
                case 0 -> var.getIdentifier();
                case 1 -> var.getRegexString();
                case 2 -> var.getHostFilter() != null ? var.getHostFilter() : "(all hosts)";
                case 3 -> var.isCaptureFromRequests() ? "✓" : "";
                case 4 -> var.getValue() != null ? var.getValue() : "(no match yet)";
                default -> "";
            };
        }
    }
}
