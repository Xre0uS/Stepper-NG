package com.xreous.stepperng.variable.view;

import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.StaticGlobalVariable;
import com.xreous.stepperng.variable.listener.StepVariableListener;
import com.xreous.stepperng.variable.StepVariable;

import javax.swing.*;
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

    public DynamicGlobalVariablesPanel(DynamicGlobalVariableManager manager) {
        super(new BorderLayout());
        this.manager = manager;


        this.staticTableModel = new StaticVarTableModel();
        this.staticTable = new JTable(staticTableModel);
        staticTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        staticTable.getColumnModel().getColumn(1).setPreferredWidth(400);
        staticTable.setRowHeight(staticTable.getFontMetrics(staticTable.getFont()).getHeight() + 8);
        staticTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedStatic();
            }
            @Override public void mousePressed(MouseEvent e) { handleStaticPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { handleStaticPopup(e); }
        });

        JPanel staticButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addStaticBtn = new JButton("Add");
        addStaticBtn.addActionListener(e -> showAddStaticDialog());
        JButton editStaticBtn = new JButton("Edit");
        editStaticBtn.addActionListener(e -> editSelectedStatic());
        JButton removeStaticBtn = new JButton("Remove");
        removeStaticBtn.addActionListener(e -> removeSelectedStatic());
        staticButtonPanel.add(addStaticBtn);
        staticButtonPanel.add(editStaticBtn);
        staticButtonPanel.add(removeStaticBtn);

        JPanel staticPanel = new JPanel(new BorderLayout());
        JLabel staticLabel = new JLabel("  Static Global Variables — Use $GVAR:name$ in requests. Set name and value manually.");
        staticLabel.setFont(staticLabel.getFont().deriveFont(Font.PLAIN));
        staticLabel.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
        staticPanel.add(staticLabel, BorderLayout.NORTH);
        staticPanel.add(new JScrollPane(staticTable), BorderLayout.CENTER);
        staticPanel.add(staticButtonPanel, BorderLayout.SOUTH);

        // Dynamic variables section
        this.dynamicTableModel = new DynamicVarTableModel();
        this.dynamicTable = new JTable(dynamicTableModel);
        dynamicTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        dynamicTable.getColumnModel().getColumn(1).setPreferredWidth(280);
        dynamicTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        dynamicTable.getColumnModel().getColumn(3).setPreferredWidth(35);
        dynamicTable.getColumnModel().getColumn(3).setMaxWidth(45);
        dynamicTable.getColumnModel().getColumn(4).setPreferredWidth(200);
        dynamicTable.setRowHeight(dynamicTable.getFontMetrics(dynamicTable.getFont()).getHeight() + 8);
        dynamicTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedDynamic();
            }
            @Override public void mousePressed(MouseEvent e) { handleDynamicPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { handleDynamicPopup(e); }
        });

        JPanel dynamicButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addDynBtn = new JButton("Add");
        addDynBtn.addActionListener(e -> showAddDynamicDialog());
        JButton editDynBtn = new JButton("Edit");
        editDynBtn.addActionListener(e -> editSelectedDynamic());
        JButton removeDynBtn = new JButton("Remove");
        removeDynBtn.addActionListener(e -> removeSelectedDynamic());
        dynamicButtonPanel.add(addDynBtn);
        dynamicButtonPanel.add(editDynBtn);
        dynamicButtonPanel.add(removeDynBtn);

        JPanel dynamicPanel = new JPanel(new BorderLayout());
        JLabel dynamicLabel = new JLabel("  Dynamic Global Variables — Use $DVAR:name$ in requests. Auto-extracts from HTTP responses (optionally requests) via regex.");
        dynamicLabel.setFont(dynamicLabel.getFont().deriveFont(Font.PLAIN));
        dynamicLabel.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
        dynamicPanel.add(dynamicLabel, BorderLayout.NORTH);
        dynamicPanel.add(new JScrollPane(dynamicTable), BorderLayout.CENTER);
        dynamicPanel.add(dynamicButtonPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, staticPanel, dynamicPanel);
        splitPane.setResizeWeight(0.4);
        this.add(splitPane, BorderLayout.CENTER);

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
        JMenuItem copyRef = new JMenuItem("Copy Variable Reference: " + usage);
        copyRef.addActionListener(a -> copyToClipboard(usage));
        menu.add(copyRef);

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

        menu.addSeparator();
        JMenuItem editItem = new JMenuItem("Edit");
        editItem.addActionListener(a -> editSelectedStatic());
        menu.add(editItem);
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
        JMenuItem copyRef = new JMenuItem("Copy Variable Reference: " + usage);
        copyRef.addActionListener(a -> copyToClipboard(usage));
        menu.add(copyRef);

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

        if (var.getRegexString() != null && !var.getRegexString().isEmpty()) {
            JMenuItem copyRegex = new JMenuItem("Copy Regex");
            copyRegex.addActionListener(a -> copyToClipboard(var.getRegexString()));
            menu.add(copyRegex);
        }

        menu.addSeparator();
        JMenuItem editItem = new JMenuItem("Edit");
        editItem.addActionListener(a -> editSelectedDynamic());
        menu.add(editItem);
        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(a -> removeSelectedDynamic());
        menu.add(removeItem);

        menu.show(dynamicTable, e.getX(), e.getY());
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /**
     * Makes Enter key in text fields trigger the OK button of the enclosing JOptionPane.
     */
    private static void enableEnterToClose(JTextField... fields) {
        for (JTextField field : fields) {
            field.addActionListener(e -> {
                JOptionPane pane = findOptionPane(field);
                if (pane != null) pane.setValue(JOptionPane.OK_OPTION);
                Window w = SwingUtilities.getWindowAncestor(field);
                if (w != null) w.dispose();
            });
        }
    }

    private static JOptionPane findOptionPane(Component c) {
        while (c != null) {
            if (c instanceof JOptionPane) return (JOptionPane) c;
            c = c.getParent();
        }
        return null;
    }

    private void refreshTables() {
        SwingUtilities.invokeLater(() -> {
            staticTableModel.fireTableDataChanged();
            dynamicTableModel.fireTableDataChanged();
        });
    }

    // Static variable dialogs

    private void showAddStaticDialog() {
        JTextField nameField = new JTextField(20);
        JTextField valueField = new JTextField(40);
        enableEnterToClose(nameField, valueField);
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        panel.add(new JLabel("Variable Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Value:"));
        panel.add(valueField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Static Global Variable",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            manager.addStaticVariable(new StaticGlobalVariable(
                    nameField.getText().trim(), valueField.getText()));
        }
    }

    private void editSelectedStatic() {
        int row = staticTable.getSelectedRow();
        if (row < 0) return;
        StaticGlobalVariable var = manager.getStaticVariables().get(row);

        JTextField nameField = new JTextField(var.getIdentifier(), 20);
        JTextField valueField = new JTextField(var.getValue(), 40);
        enableEnterToClose(nameField, valueField);
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        panel.add(new JLabel("Variable Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Value:"));
        panel.add(valueField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Static Global Variable",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            var.setIdentifier(nameField.getText().trim());
            var.setValue(valueField.getText());
            manager.notifyVariableChanged(var);
            staticTableModel.fireTableDataChanged();
        }
    }

    private void removeSelectedStatic() {
        int row = staticTable.getSelectedRow();
        if (row < 0) return;
        manager.removeStaticVariable(manager.getStaticVariables().get(row));
    }

    // Dynamic variable dialogs

    private void showAddDynamicDialog() {
        JTextField nameField = new JTextField(20);
        JTextField regexField = new JTextField(40);
        JTextField hostField = new JTextField(20);
        JCheckBox captureRequestsCheckbox = new JCheckBox("Also capture from requests");
        enableEnterToClose(nameField, regexField, hostField);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Variable Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1; panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; panel.add(new JLabel("Regex (group 1 captured):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1; panel.add(regexField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; panel.add(new JLabel("Host Filter (optional regex):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1; panel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; panel.add(captureRequestsCheckbox, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Dynamic Global Variable",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            manager.addVariable(new DynamicGlobalVariable(
                    nameField.getText().trim(),
                    regexField.getText().trim(),
                    hostField.getText().trim().isEmpty() ? null : hostField.getText().trim(),
                    captureRequestsCheckbox.isSelected()));
        }
    }

    private void editSelectedDynamic() {
        int row = dynamicTable.getSelectedRow();
        if (row < 0) return;
        DynamicGlobalVariable var = manager.getVariables().get(row);

        JTextField nameField = new JTextField(var.getIdentifier(), 20);
        JTextField regexField = new JTextField(var.getRegexString(), 40);
        JTextField hostField = new JTextField(var.getHostFilter() != null ? var.getHostFilter() : "", 20);
        JCheckBox captureRequestsCheckbox = new JCheckBox("Also capture from requests", var.isCaptureFromRequests());
        enableEnterToClose(nameField, regexField, hostField);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Variable Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1; panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; panel.add(new JLabel("Regex (group 1 captured):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1; panel.add(regexField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; panel.add(new JLabel("Host Filter (optional regex):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1; panel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; panel.add(captureRequestsCheckbox, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Dynamic Global Variable",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            var.setIdentifier(nameField.getText().trim());
            var.setRegex(regexField.getText().trim());
            var.setHostFilter(hostField.getText().trim().isEmpty() ? null : hostField.getText().trim());
            var.setCaptureFromRequests(captureRequestsCheckbox.isSelected());
            manager.notifyVariableChanged(var);
            dynamicTableModel.fireTableDataChanged();
        }
    }

    private void removeSelectedDynamic() {
        int row = dynamicTable.getSelectedRow();
        if (row < 0) return;
        manager.removeVariable(manager.getVariables().get(row));
    }

    // Table models

    private class StaticVarTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Name ($GVAR:name$)", "Value"};

        @Override public int getRowCount() { return manager.getStaticVariables().size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
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

        @Override
        public Object getValueAt(int row, int col) {
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

