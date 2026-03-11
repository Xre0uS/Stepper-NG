package com.xreous.stepperng.sequence.view;

import com.coreyd97.BurpExtenderUtilities.CustomTabComponent;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.listener.StepAdapter;
import com.xreous.stepperng.step.view.StepPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.function.Consumer;

public class SequenceContainer extends JPanel {

    private final StepSequence stepSequence;
    private final HashMap<Step, StepPanel> stepToPanelMap;
    private final JTabbedPane tabbedContainer;
    private final SequenceOverviewPanel overviewPanel;


    private int dragSourceTabIndex = -1;
    private int dragTargetTabIndex = -1;
    private boolean isDragging = false;
    private CustomTabComponent dragSourceTabComponent = null;
    private boolean suppressTabSwitch = false;
    private int userSelectedTabIndex = 0;

    public SequenceContainer(StepSequence stepSequence){
        super(new BorderLayout());
        this.stepSequence = stepSequence;
        this.stepToPanelMap = new HashMap<>();

        this.tabbedContainer = new JTabbedPane();

        tabbedContainer.addChangeListener(e -> {
            if (!suppressTabSwitch) {
                userSelectedTabIndex = tabbedContainer.getSelectedIndex();
            }
        });

        this.overviewPanel = new SequenceOverviewPanel(stepSequence);
        tabbedContainer.addTab("Overview", overviewPanel);

        tabbedContainer.addTab("Add Step", null);
        CustomTabComponent addStepTab = new CustomTabComponent("Add Step");
        tabbedContainer.setTabComponentAt(1, addStepTab);
        addStepTab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    stepSequence.addStep();
                }
            }
        });

        this.add(tabbedContainer, BorderLayout.CENTER);

        for (Step step : this.stepSequence.getSteps()) {
            addPanelForStep(step);
        }
        refreshAllStepCombos();

        this.stepSequence.addStepListener(new StepAdapter(){
            @Override
            public void onStepAdded(Step step) {
                addPanelForStep(step);
                refreshAllStepCombos();
            }
            @Override
            public void onStepRemoved(Step step) {
                removePanelForStep(step);
                refreshAllStepCombos();
            }
            @Override
            public void onStepUpdated(Step step) {
                SwingUtilities.invokeLater(() -> {
                    syncTabOrderFromModel();
                    StepPanel panel = stepToPanelMap.get(step);
                    if (panel != null) panel.refreshValidationState();
                    refreshAllStepCombos();
                });
            }
        });
    }

    private int getAddStepTabIndex() {
        return tabbedContainer.getTabCount() - 1;
    }

    private int getStepCount() {
        return getAddStepTabIndex() - 1;
    }

    private boolean isStepTab(int tabIndex) {
        return tabIndex >= 1 && tabIndex < getAddStepTabIndex();
    }

    private boolean isDragAllowed(int tabIndex) {
        if (!isStepTab(tabIndex)) return false;
        Integer valIdx = stepSequence.getValidationStepIndex();
        if (valIdx != null && (tabIndex - 1) == valIdx) return false;
        return true;
    }

    private boolean isDropAllowed(int tabIndex) {
        if (!isStepTab(tabIndex)) return false;
        Integer valIdx = stepSequence.getValidationStepIndex();
        if (valIdx != null && valIdx == 0 && tabIndex == 1) return false;
        return true;
    }

    private void performTabMove(int fromTabIndex, int toTabIndex) {
        if (fromTabIndex == toTabIndex) return;
        if (!isStepTab(fromTabIndex) || !isStepTab(toTabIndex)) return;

        int fromStepIdx = fromTabIndex - 1;
        int toStepIdx = toTabIndex - 1;

        stepSequence.moveStep(fromStepIdx, toStepIdx);
    }

    private void addTabForStep(Step step, StepPanel panel){
        int insertAt = getAddStepTabIndex();
        tabbedContainer.insertTab(null, null, panel, null, insertAt);

        int stepNumber = getStepCount();

        Consumer<String> onTitleChanged = newTitle -> {
            step.setTitle(newTitle);
            stepSequence.stepModified(step);
        };

        Consumer<Void> onRemoveClicked = nothing -> {
            int result = JOptionPane.showConfirmDialog(tabbedContainer,
                    "Are you sure you want to remove this step? (" + step.getTitle() + ")",
                    "Remove Step", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                this.stepSequence.removeStep(step);
            }
        };

        CustomTabComponent tabComponent = new CustomTabComponent(stepNumber,
                step.getTitle(), true,
                true, onTitleChanged, true, onRemoveClicked);

        attachDragListeners(tabComponent);
        attachContextMenuListener(tabComponent, step);

        tabbedContainer.setTabComponentAt(insertAt, tabComponent);
        tabbedContainer.setSelectedIndex(insertAt);
        updateTabAppearance(tabComponent, step);
    }

    private void attachDragListeners(CustomTabComponent tabComponent) {
        tabComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int tabIdx = tabbedContainer.indexOfTabComponent(tabComponent);
                if (isDragAllowed(tabIdx)) {
                    dragSourceTabIndex = tabIdx;
                    dragSourceTabComponent = tabComponent;
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (isDragging && dragSourceTabIndex >= 0 && dragTargetTabIndex >= 0
                        && dragSourceTabIndex != dragTargetTabIndex) {
                    performTabMove(dragSourceTabIndex, dragTargetTabIndex);
                }
                if (isDragging && dragSourceTabComponent != null) {
                    dragSourceTabComponent.setDragging(false);
                }
                dragSourceTabIndex = -1;
                dragTargetTabIndex = -1;
                isDragging = false;
                dragSourceTabComponent = null;
                tabbedContainer.setCursor(Cursor.getDefaultCursor());
            }
        });
        tabComponent.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragSourceTabIndex < 0) return;
                if (!isDragging) {
                    isDragging = true;
                    if (dragSourceTabComponent != null) {
                        dragSourceTabComponent.setDragging(true);
                    }
                    tabbedContainer.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                Point p = SwingUtilities.convertPoint(tabComponent, e.getPoint(), tabbedContainer);
                int targetIdx = tabbedContainer.indexAtLocation(p.x, p.y);
                if (isDropAllowed(targetIdx) && targetIdx != dragSourceTabIndex) {
                    dragTargetTabIndex = targetIdx;
                } else {
                    dragTargetTabIndex = -1;
                }
            }
        });
    }

    private void attachContextMenuListener(CustomTabComponent tabComponent, Step step) {
        tabComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showStepContextMenu(e, tabComponent, step);
                }
            }
        });
    }

    private void showStepContextMenu(MouseEvent e, CustomTabComponent tabComponent, Step step) {
        JPopupMenu popupMenu = new JPopupMenu();

        String toggleLabel = step.isEnabled() ? "Disable Step" : "Enable Step";
        JMenuItem toggleItem = new JMenuItem(toggleLabel);
        toggleItem.addActionListener(ae -> {
            step.setEnabled(!step.isEnabled());
            updateTabAppearance(tabComponent, step);
            stepSequence.stepModified(step);
        });
        popupMenu.add(toggleItem);
        popupMenu.addSeparator();

        JMenu moveStep = new JMenu("Move Step");
        int fromTabIndex = tabbedContainer.indexOfTabComponent(tabComponent);
        int fromStepIdx = fromTabIndex - 1;

        Integer valIdx = stepSequence.getValidationStepIndex();
        boolean isValidationStep = valIdx != null && fromStepIdx == valIdx;

        if (isValidationStep) {
            JMenuItem locked = new JMenuItem("(Validation step — locked)");
            locked.setEnabled(false);
            moveStep.add(locked);
        } else {
            for (int i = 1; i < getAddStepTabIndex(); i++) {
                if (i == fromTabIndex) continue;
                if (!isDropAllowed(i)) continue;
                int targetTabIdx = i;
                Step targetStep = stepSequence.getSteps().get(i - 1);
                JMenuItem moveTo = new JMenuItem("Step " + i + ": " + targetStep.getTitle());
                moveTo.addActionListener(ae -> performTabMove(fromTabIndex, targetTabIdx));
                moveStep.add(moveTo);
            }
        }

        popupMenu.add(moveStep);
        popupMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void updateTabAppearance(CustomTabComponent tabComponent, Step step) {
        int tabIndex = tabbedContainer.indexOfTabComponent(tabComponent);
        int stepNum = tabIndex;

        tabComponent.setIndex(stepNum);

        String displayTitle = step.isEnabled() ? step.getTitle() : "⊘ " + step.getTitle();
        tabComponent.setTitle(displayTitle);

        Color fg = step.isEnabled()
                ? UIManager.getColor("TabbedPane.foreground")
                : UIManager.getColor("Label.disabledForeground");
        if (fg == null) fg = step.isEnabled() ? Color.BLACK : Color.GRAY;

        for (Component c : tabComponent.getComponents()) {
            if (c instanceof JLabel label) {
                label.setForeground(fg);
            }
        }
        tabComponent.repaint();
    }

    private void updateTabIndices(){
        for (int i = 1; i < getAddStepTabIndex(); i++) {
            Component c = tabbedContainer.getTabComponentAt(i);
            if (c instanceof CustomTabComponent tab) {
                tab.setIndex(i);
                Step step = stepSequence.getSteps().get(i - 1);
                String displayTitle = step.isEnabled() ? step.getTitle() : "⊘ " + step.getTitle();
                tab.setTitle(displayTitle);
            }
        }
        if (overviewPanel != null) overviewPanel.refresh();
    }

    private void syncTabOrderFromModel() {
        var modelSteps = stepSequence.getSteps();
        boolean needsSync = false;
        for (int i = 0; i < modelSteps.size(); i++) {
            int tabIdx = i + 1;
            if (tabIdx >= getAddStepTabIndex()) { needsSync = true; break; }
            Component tabBody = tabbedContainer.getComponentAt(tabIdx);
            StepPanel expectedPanel = stepToPanelMap.get(modelSteps.get(i));
            if (tabBody != expectedPanel) {
                needsSync = true;
                break;
            }
        }
        if (!needsSync) {
            updateTabIndices();
            return;
        }

        int selectedStepIdx = tabbedContainer.getSelectedIndex();
        Component selectedBody = (selectedStepIdx >= 0 && selectedStepIdx < tabbedContainer.getTabCount())
                ? tabbedContainer.getComponentAt(selectedStepIdx) : null;

        while (getAddStepTabIndex() > 1) {
            tabbedContainer.removeTabAt(1);
        }

        for (int i = 0; i < modelSteps.size(); i++) {
            Step step = modelSteps.get(i);
            StepPanel panel = stepToPanelMap.get(step);
            if (panel == null) continue;
            int insertAt = getAddStepTabIndex();
            tabbedContainer.insertTab(null, null, panel, null, insertAt);

            Consumer<String> onTitleChanged = newTitle -> {
                step.setTitle(newTitle);
                stepSequence.stepModified(step);
            };
            Consumer<Void> onRemoveClicked = nothing -> {
                int result = JOptionPane.showConfirmDialog(tabbedContainer,
                        "Are you sure you want to remove this step? (" + step.getTitle() + ")",
                        "Remove Step", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    this.stepSequence.removeStep(step);
                }
            };
            CustomTabComponent tabComp = new CustomTabComponent(i + 1,
                    step.getTitle(), true,
                    true, onTitleChanged, true, onRemoveClicked);
            attachDragListeners(tabComp);
            attachContextMenuListener(tabComp, step);
            tabbedContainer.setTabComponentAt(insertAt, tabComp);
            updateTabAppearance(tabComp, step);
        }

        if (selectedBody != null) {
            int restoredIdx = tabbedContainer.indexOfComponent(selectedBody);
            if (restoredIdx >= 0) tabbedContainer.setSelectedIndex(restoredIdx);
        }
        updateTabIndices();
    }

    private void removeTabbedEntry(StepPanel stepPanel){
        tabbedContainer.remove(stepPanel);
        if (tabbedContainer.getSelectedIndex() >= getAddStepTabIndex()) {
            tabbedContainer.setSelectedIndex(Math.max(0, getAddStepTabIndex() - 1));
        }
        updateTabIndices();
    }

    private void addPanelForStep(Step step){
        StepPanel panel = new StepPanel(this, step);
        this.stepToPanelMap.put(step, panel);
        addTabForStep(step, panel);
        this.revalidate();
        this.repaint();
    }

    private void removePanelForStep(Step step){
        StepPanel panel = this.stepToPanelMap.remove(step);
        updateSubsequentPanels(panel);
        removeTabbedEntry(panel);
        this.revalidate();
        this.repaint();
    }

    public StepPanel getPanelForStep(Step step){
        return this.stepToPanelMap.get(step);
    }

    private void refreshAllStepCombos() {
        SwingUtilities.invokeLater(() -> {
            for (StepPanel panel : stepToPanelMap.values()) {
                panel.refreshStepCombos();
            }
        });
    }

    public void setActivePanel(StepPanel stepPanel){
        if (!suppressTabSwitch) {
            this.tabbedContainer.setSelectedComponent(stepPanel);
        }
    }

    public void beginExecution() {
        suppressTabSwitch = true;
    }

    public void endExecution() {
        suppressTabSwitch = false;
        if (userSelectedTabIndex >= 0 && userSelectedTabIndex < tabbedContainer.getTabCount()) {
            tabbedContainer.setSelectedIndex(userSelectedTabIndex);
        }
    }

    public void updateSubsequentPanels(StepPanel panel){
        int tabIndex = this.tabbedContainer.indexOfComponent(panel) + 1;
        for (; tabIndex < getAddStepTabIndex(); tabIndex++) {
            Component comp = tabbedContainer.getComponentAt(tabIndex);
            if (comp instanceof StepPanel sp) sp.refreshRequestPanel();
        }
    }

    public StepPanel getSelectedStepPanel() {
        Component selectedTab = this.tabbedContainer.getSelectedComponent();
        if (selectedTab instanceof StepPanel sp) return sp;
        return null;
    }
}
