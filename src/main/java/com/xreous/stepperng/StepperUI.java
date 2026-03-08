package com.xreous.stepperng;

import com.coreyd97.BurpExtenderUtilities.CustomTabComponent;
import com.coreyd97.BurpExtenderUtilities.PopOutPanel;
import com.xreous.stepperng.sequencemanager.listener.StepSequenceListener;
import com.xreous.stepperng.about.view.AboutPanel;
import com.xreous.stepperng.preferences.view.OptionsPanel;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequence.view.StepSequenceTab;
import com.xreous.stepperng.sequencemanager.SequenceManager;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.view.DynamicGlobalVariablesPanel;
import com.google.gson.Gson;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.function.Consumer;

public class StepperUI {

    private final SequenceManager sequenceManager;
    private final JTabbedPane tabbedPane;
    private final PopOutPanel popOutPanel;
    private final HashMap<StepSequence, StepSequenceTab> managerTabMap;
    private boolean hasUnseen = false;

    public StepperUI(SequenceManager sequenceManager, DynamicGlobalVariableManager dynamicVarManager){
        this.sequenceManager = sequenceManager;
        this.managerTabMap = new HashMap<>();

        this.tabbedPane = new JTabbedPane();
        CustomTabComponent addSequenceTabComponent = new CustomTabComponent( "Add Sequence");
        addSequenceTabComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e)) {
                    sequenceManager.addStepSequence(new StepSequence());
                }
            }
        });
        this.tabbedPane.addTab("Add Sequence", null);
        this.tabbedPane.setTabComponentAt(0, addSequenceTabComponent);

        this.tabbedPane.addTab("Global Variables", new DynamicGlobalVariablesPanel(dynamicVarManager));
        this.tabbedPane.addTab("Preferences", new OptionsPanel(this.sequenceManager));
        this.tabbedPane.addTab("About", new AboutPanel());

        // Register Ctrl+Shift+G to execute the currently selected sequence
        InputMap im = this.tabbedPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "ExecuteSelectedSequence");
        this.tabbedPane.getActionMap().put("ExecuteSelectedSequence", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (Stepper.getPreferences() != null
                        && Boolean.TRUE.equals(Stepper.getPreferences().getSetting(Globals.PREF_ENABLE_SHORTCUT))) {
                    StepSequenceTab selected = getSelectedStepSet();
                    if (selected != null) {
                        SwingUtilities.invokeLater(selected.getStepSequence()::executeAsync);
                    }
                }
            }
        });

        this.popOutPanel = new PopOutPanel(Stepper.montoya, this.tabbedPane, "Stepper-NG");

        this.popOutPanel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && popOutPanel.isShowing()) {
                clearHighlight();
                clearAllSequenceHighlights();
            }
        });

        this.tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            clearSequenceHighlight(idx);
        });

        for (StepSequence sequence : this.sequenceManager.getSequences()) {
            addTabForSequence(sequence);
        }

        if(this.sequenceManager.getSequences().size() == 0){
            this.tabbedPane.setSelectedIndex(3);
        }

        this.sequenceManager.addStepSequenceListener(new StepSequenceListener() {
            @Override
            public void onStepSequenceAdded(StepSequence sequence) {
                addTabForSequence(sequence);
            }

            @Override
            public void onStepSequenceRemoved(StepSequence sequence) {
                removeTabForSequence(sequence);
            }
        });
    }

    private void addTabForSequence(StepSequence sequence){
        StepSequenceTab tab = new StepSequenceTab(sequence);
        managerTabMap.put(sequence, tab);
        int newTabLocation = this.tabbedPane.getTabCount()-4;
        this.tabbedPane.insertTab("", null, tab, null, newTabLocation);

        Consumer<String> onTitleChange = newTitle -> {
            String cleanTitle = newTitle.startsWith("⊘ ") ? newTitle.substring(2) : newTitle;
            sequence.setTitle(cleanTitle);
            if (sequence.isDisabled()) {
                int idx = tabbedPane.indexOfComponent(tab);
                if (idx >= 0) {
                    Component tc = tabbedPane.getTabComponentAt(idx);
                    if (tc instanceof CustomTabComponent ctc) {
                        updateSequenceTabTitle(sequence, ctc);
                    }
                }
            }
        };

        Consumer<Void> onRemoveClicked = aVoid -> {
            int result = JOptionPane.showConfirmDialog(tabbedPane, "Are you sure you want to remove this sequence? (" + sequence.getTitle() + ")", "Remove Sequence", JOptionPane.YES_NO_OPTION);
            if(result == JOptionPane.YES_OPTION) {
                this.sequenceManager.removeStepSequence(sequence);
            }
        };

        CustomTabComponent tabComponent = new CustomTabComponent( newTabLocation-1,
                sequence.getTitle(), false,
                true, onTitleChange, true, onRemoveClicked);

        this.tabbedPane.setTabComponentAt(newTabLocation, tabComponent);
        this.tabbedPane.setSelectedIndex(newTabLocation);

        tabComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu popup = new JPopupMenu();

                    boolean isDisabled = sequence.isDisabled();
                    JMenuItem toggleItem = new JMenuItem(isDisabled ? "Enable Sequence" : "Disable Sequence");
                    toggleItem.addActionListener(ae -> {
                        sequence.setDisabled(!isDisabled);
                        updateSequenceTabTitle(sequence, tabComponent);
                    });
                    popup.add(toggleItem);

                    JMenuItem duplicateItem = new JMenuItem("Duplicate Sequence");
                    duplicateItem.addActionListener(ae -> duplicateSequence(sequence));
                    popup.add(duplicateItem);

                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        if (sequence.isDisabled()) {
            updateSequenceTabTitle(sequence, tabComponent);
        }
    }

    private void updateSequenceTabTitle(StepSequence sequence, CustomTabComponent tabComponent) {
        String title = sequence.getTitle();
        if (sequence.isDisabled()) {
            tabComponent.setTitle("⊘ " + title);
            tabComponent.setToolTipText("Sequence disabled — variables passed as literal text");
        } else {
            tabComponent.setTitle(title);
            tabComponent.setToolTipText(null);
        }
    }

    private void duplicateSequence(StepSequence original) {
        try {
            Gson gson = Stepper.getGsonProvider().getGson();
            String json = gson.toJson(original, StepSequence.class);
            StepSequence copy = gson.fromJson(json, StepSequence.class);
            copy.setTitle(original.getTitle() + " (Copy)");
            sequenceManager.addStepSequence(copy);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(tabbedPane, "Failed to duplicate sequence: " + e.getMessage(),
                    "Duplicate Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeTabForSequence(StepSequence sequence){
        StepSequenceTab stepSequenceTab = this.getTabForStepManager(sequence);
        int removedIndex = this.tabbedPane.indexOfComponent(stepSequenceTab);
        this.tabbedPane.remove(stepSequenceTab);
        this.managerTabMap.remove(sequence);

        if(removedIndex == 0 && this.managerTabMap.size() == 0){ //If we removed the leftmost tab and have no other tabs
            this.tabbedPane.setSelectedIndex(3); //View the about tab
        }else if(removedIndex == this.tabbedPane.getTabCount() - 4 && this.managerTabMap.size() > 0) {
            this.tabbedPane.setSelectedIndex(removedIndex-1);
        }
    }

    public StepSequenceTab getTabForStepManager(StepSequence manager){
        return this.managerTabMap.get(manager);
    }

    public StepSequenceTab getSelectedStepSet(){
        if(!getUiComponent().isVisible()) return null;
        Component selectedStepSet = this.tabbedPane.getSelectedComponent();
        if(selectedStepSet instanceof StepSequenceTab)
            return (StepSequenceTab) selectedStepSet;
        else
            return null;
    }

    public Component getUiComponent() {
        return this.popOutPanel;
    }

    public void highlightTab() {
        if (hasUnseen) return;
        hasUnseen = true;
        SwingUtilities.invokeLater(() -> {
            JTabbedPane burpTabs = findBurpSuiteTabs();
            if (burpTabs == null) return;
            int idx = findStepperTabIndex(burpTabs);
            if (idx < 0) return;

            String title = burpTabs.getTitleAt(idx);
            if (title != null && !title.endsWith(" ●")) {
                burpTabs.setTitleAt(idx, title + " ●");
            }

            Component tabComp = burpTabs.getTabComponentAt(idx);
            if (tabComp instanceof CustomTabComponent ctc) {
                ctc.showDot();
            } else if (tabComp != null) {
                addDotToSwingLabel(tabComp, true);
            }
        });
    }

    private void clearHighlight() {
        if (!hasUnseen) return;
        hasUnseen = false;
        SwingUtilities.invokeLater(() -> {
            JTabbedPane burpTabs = findBurpSuiteTabs();
            if (burpTabs == null) return;
            int idx = findStepperTabIndex(burpTabs);
            if (idx < 0) return;

            String title = burpTabs.getTitleAt(idx);
            if (title != null && title.endsWith(" ●")) {
                burpTabs.setTitleAt(idx, title.substring(0, title.length() - 2));
            }

            Component tabComp = burpTabs.getTabComponentAt(idx);
            if (tabComp instanceof CustomTabComponent ctc) {
                ctc.hideDot();
            } else if (tabComp != null) {
                addDotToSwingLabel(tabComp, false);
            }
        });
    }

    private void addDotToSwingLabel(Component comp, boolean show) {
        if (comp instanceof JLabel label) {
            String text = label.getText();
            if (text == null) return;
            if (show && !text.endsWith(" ●")) {
                label.setText(text + " ●");
                label.setForeground(new Color(0xE5, 0x6B, 0x22));
            } else if (!show && text.endsWith(" ●")) {
                label.setText(text.substring(0, text.length() - 2));
                label.setForeground(null);
            }
        }
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                addDotToSwingLabel(child, show);
            }
        }
    }

    public void highlightSequenceTab(StepSequence sequence) {
        SwingUtilities.invokeLater(() -> {
            StepSequenceTab tab = managerTabMap.get(sequence);
            if (tab == null) return;
            int tabIdx = tabbedPane.indexOfComponent(tab);
            if (tabIdx < 0) return;
            Component tabComp = tabbedPane.getTabComponentAt(tabIdx);
            if (tabComp instanceof CustomTabComponent ctc) {
                ctc.showDot();
            }
        });
    }

    private void clearSequenceHighlight(int tabIdx) {
        if (tabIdx < 0 || tabIdx >= tabbedPane.getTabCount()) return;
        Component tabComp = tabbedPane.getTabComponentAt(tabIdx);
        if (tabComp instanceof CustomTabComponent ctc) {
            ctc.hideDot();
        }
    }

    private void clearAllSequenceHighlights() {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component tabComp = tabbedPane.getTabComponentAt(i);
            if (tabComp instanceof CustomTabComponent ctc && ctc.isDotVisible()) {
                ctc.hideDot();
            }
        }
    }

    private JTabbedPane findBurpSuiteTabs() {
        Component c = popOutPanel;
        while (c != null) {
            c = c.getParent();
            if (c instanceof JTabbedPane tp) {
                for (int i = 0; i < tp.getTabCount(); i++) {
                    if (tp.getComponentAt(i) == popOutPanel || isAncestorOf(tp.getComponentAt(i), popOutPanel)) {
                        return tp;
                    }
                }
            }
        }
        return null;
    }

    private int findStepperTabIndex(JTabbedPane burpTabs) {
        for (int i = 0; i < burpTabs.getTabCount(); i++) {
            Component comp = burpTabs.getComponentAt(i);
            if (comp == popOutPanel || isAncestorOf(comp, popOutPanel)) return i;
        }
        return -1;
    }

    private boolean isAncestorOf(Component ancestor, Component child) {
        Component c = child;
        while (c != null) {
            if (c == ancestor) return true;
            c = c.getParent();
        }
        return false;
    }

    public boolean isStepperTabVisible() {
        JTabbedPane burpTabs = findBurpSuiteTabs();
        if (burpTabs == null) return false;
        int idx = findStepperTabIndex(burpTabs);
        return idx >= 0 && burpTabs.getSelectedIndex() == idx;
    }

    public boolean isSequenceVisible(StepSequence sequence) {
        if (!isStepperTabVisible()) return false;
        StepSequenceTab tab = managerTabMap.get(sequence);
        if (tab == null) return false;
        int idx = tabbedPane.indexOfComponent(tab);
        return idx >= 0 && tabbedPane.getSelectedIndex() == idx;
    }
}
