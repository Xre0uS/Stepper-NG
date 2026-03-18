package com.xreous.stepperng;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequencemanager.SequenceManager;
import com.xreous.stepperng.step.view.StepPanel;
import com.xreous.stepperng.sequence.view.StepSequenceTab;
import com.xreous.stepperng.util.AutoRegexDialog;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.StaticGlobalVariable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ContextMenuFactory implements ContextMenuItemsProvider {

    private final SequenceManager sequenceManager;

    public ContextMenuFactory(SequenceManager sequenceManager){
        this.sequenceManager = sequenceManager;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<HttpRequestResponse> messages = new ArrayList<>();

        if (event.selectedRequestResponses() != null) {
            messages.addAll(event.selectedRequestResponses());
        }

        if (messages.isEmpty() && event.messageEditorRequestResponse().isPresent()) {
            messages.add(event.messageEditorRequestResponse().get().requestResponse());
        }

        ArrayList<Component> menuItems = new ArrayList<>();

        if (!messages.isEmpty()) {
            final List<HttpRequestResponse> finalMessages = messages;
            String addMenuTitle = String.format("Add %d %s to Stepper-NG", messages.size(), messages.size() == 1 ? "item" : "items");
            JMenu addStepMenu = new JMenu(addMenuTitle);

            for (StepSequence sequence : this.sequenceManager.getSequences()) {
                JMenuItem item = new JMenuItem(sequence.getTitle());
                item.addActionListener(actionEvent -> {
                    for (HttpRequestResponse message : finalMessages) {
                        sequence.addStep(message);
                    }
                });
                addStepMenu.add(item);
            }

            JMenuItem newSequence = new JMenuItem("New Sequence");
            newSequence.addActionListener(actionEvent -> {
                String name = JOptionPane.showInputDialog(Stepper.getUI() != null ? Stepper.getUI().getUiComponent() : null,
                        "Enter a name to identify the sequence: ", "", JOptionPane.PLAIN_MESSAGE);
                if (name != null) {
                    StepSequence stepSequence = new StepSequence(name);
                    for (HttpRequestResponse message : finalMessages) {
                        stepSequence.addStep(message);
                    }
                    this.sequenceManager.addStepSequence(stepSequence);
                }
            });

            addStepMenu.add(new JPopupMenu.Separator());
            addStepMenu.add(newSequence);
            menuItems.add(addStepMenu);
        }

        List<Component> headerItems = buildCopyHeaderMenuItems();
        if (!headerItems.isEmpty()) menuItems.addAll(headerItems);
        List<Component> varItems = buildVariableMenuItems();
        if (!varItems.isEmpty()) menuItems.addAll(varItems);
        List<Component> dvarItems = buildDynamicVarMenuItems();
        if (!dvarItems.isEmpty()) menuItems.addAll(dvarItems);
        List<Component> gvarItems = buildStaticVarMenuItems();
        if (!gvarItems.isEmpty()) menuItems.addAll(gvarItems);

        // Auto-regex from message editor
        List<Component> autoRegexItems = buildAutoRegexMenuItems(event);
        if (!autoRegexItems.isEmpty()) menuItems.addAll(autoRegexItems);

        return menuItems;
    }

    private List<Component> buildCopyHeaderMenuItems(){
        List<Component> menuItems = new ArrayList<>();

        JMenu addStepHeaderToClipboardMenu = new JMenu("Copy Header To Clipboard");

        for (StepSequence stepSequence : sequenceManager.getSequences()) {
            JMenu sequenceItem = new JMenu(stepSequence.getTitle());

            JMenuItem execBeforeMenuItem = new JMenuItem("Execute-Before Header");
            execBeforeMenuItem.addActionListener(actionEvent -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(MessageProcessor.EXECUTE_BEFORE_HEADER+": " + stepSequence.getTitle()), null);
            });
            sequenceItem.add(execBeforeMenuItem);

            JMenuItem execAfterMenuItem = new JMenuItem("Execute-After Header");
            execAfterMenuItem.addActionListener(actionEvent -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(MessageProcessor.EXECUTE_AFTER_HEADER+": " + stepSequence.getTitle()), null);
            });
            sequenceItem.add(execAfterMenuItem);

            addStepHeaderToClipboardMenu.add(sequenceItem);
        }

        menuItems.add(addStepHeaderToClipboardMenu);

        return menuItems;
    }

    private List<Component> buildVariableMenuItems(){
        List<Component> menuItems = new ArrayList<>();

        HashMap<StepSequence, List<StepVariable>> sequenceVariableMap = new HashMap<>();

        boolean isViewingSequenceStep = false;
        if (Stepper.getUI() != null) {
            StepSequenceTab selectedStepSet = Stepper.getUI().getSelectedStepSet();
            if(selectedStepSet != null){
                StepPanel selectedStepPanel = selectedStepSet.getSelectedStepPanel();
                if(selectedStepPanel != null){
                    isViewingSequenceStep = true;
                    StepSequence seq = selectedStepSet.getStepSequence();
                    List<StepVariable> allSeqVariables = seq.getRollingVariablesForWholeSequence();
                    sequenceVariableMap.put(seq, allSeqVariables);
                }
            }
        }
        if (!isViewingSequenceStep) {
            sequenceVariableMap = sequenceManager.getRollingVariablesFromAllSequences();
        }

        long varCount = sequenceVariableMap.values().stream().mapToInt(List::size).sum();

        if(varCount > 0) {
            JMenu addStepVariableToClipboardMenu = new JMenu("Copy Variable To Clipboard");

            if(isViewingSequenceStep){
                Collection<StepVariable> variables = sequenceVariableMap.values().stream()
                        .flatMap(Collection::stream).collect(Collectors.toList());

                List<JMenuItem> variableToClipboardMenuItems = buildAddVariableToClipboardMenuItems(null, variables);

                for (JMenuItem item : variableToClipboardMenuItems) {
                    addStepVariableToClipboardMenu.add(item);
                }
            }else{
                for (Map.Entry<StepSequence, List<StepVariable>> entry : sequenceVariableMap.entrySet()) {
                    StepSequence stepSequence = entry.getKey();
                    List<StepVariable> stringStepVariableHashMap = entry.getValue();
                    if (stringStepVariableHashMap.size() > 0) {
                        JMenu sequenceItem = new JMenu(stepSequence.getTitle());
                        List<JMenuItem> sequenceVariableToClipboardItems =
                                ContextMenuFactory.this.buildAddVariableToClipboardMenuItems(stepSequence, stringStepVariableHashMap);
                        for (JMenuItem item : sequenceVariableToClipboardItems) {
                            sequenceItem.add(item);
                        }
                        addStepVariableToClipboardMenu.add(sequenceItem);
                    }
                }
            }

            menuItems.add(addStepVariableToClipboardMenu);
        }

        return menuItems;
    }

    private List<JMenuItem> buildAddVariableToClipboardMenuItems(StepSequence sequence, Collection<StepVariable> variables){
        List<JMenuItem> menuItems = new ArrayList<>();
        for (StepVariable variable : variables) {
            JMenuItem item = new JMenuItem(variable.getIdentifier());
            item.addActionListener(actionEvent -> {
                String variableString;
                if(sequence == null){
                    variableString = StepVariable.createVariableString(variable.getIdentifier());
                }else{
                    variableString = StepVariable.createVariableString(sequence.getTitle(), variable.getIdentifier());
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(variableString), null);
            });
            menuItems.add(item);
        }
        return menuItems;
    }

    private List<Component> buildDynamicVarMenuItems(){
        List<Component> menuItems = new ArrayList<>();
        DynamicGlobalVariableManager dvarManager = Stepper.getDynamicGlobalVariableManager();
        if (dvarManager == null || dvarManager.getVariables().isEmpty()) return menuItems;

        JMenu dvarMenu = new JMenu("Copy Dynamic Variable To Clipboard");
        for (DynamicGlobalVariable dvar : dvarManager.getVariables()) {
            String label = dvar.getIdentifier();
            if (dvar.getValue() != null && !dvar.getValue().isEmpty()) {
                String preview = dvar.getValue().length() > 30 ? dvar.getValue().substring(0, 30) + "..." : dvar.getValue();
                label += " = " + preview;
            }
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(actionEvent -> {
                String dvarString = DynamicGlobalVariable.createDvarString(dvar.getIdentifier());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(dvarString), null);
            });
            dvarMenu.add(item);
        }
        menuItems.add(dvarMenu);
        return menuItems;
    }

    private List<Component> buildStaticVarMenuItems(){
        List<Component> menuItems = new ArrayList<>();
        DynamicGlobalVariableManager manager = Stepper.getDynamicGlobalVariableManager();
        if (manager == null || manager.getStaticVariables().isEmpty()) return menuItems;

        JMenu gvarMenu = new JMenu("Copy Global Variable To Clipboard");
        for (StaticGlobalVariable svar : manager.getStaticVariables()) {
            String label = svar.getIdentifier();
            if (svar.getValue() != null && !svar.getValue().isEmpty()) {
                String preview = svar.getValue().length() > 30 ? svar.getValue().substring(0, 30) + "..." : svar.getValue();
                label += " = " + preview;
            }
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(actionEvent -> {
                String gvarString = StaticGlobalVariable.createGvarString(svar.getIdentifier());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(gvarString), null);
            });
            gvarMenu.add(item);
        }
        menuItems.add(gvarMenu);
        return menuItems;
    }

    private List<Component> buildAutoRegexMenuItems(ContextMenuEvent event) {
        List<Component> menuItems = new ArrayList<>();

        if (event.messageEditorRequestResponse().isEmpty()) return menuItems;

        MessageEditorHttpRequestResponse editorMsg = event.messageEditorRequestResponse().get();
        HttpRequestResponse reqResp = editorMsg.requestResponse();
        if (reqResp == null) return menuItems;

        MessageEditorHttpRequestResponse.SelectionContext selCtx = editorMsg.selectionContext();
        boolean isResponse = (selCtx == MessageEditorHttpRequestResponse.SelectionContext.RESPONSE);
        boolean isRequest = (selCtx == MessageEditorHttpRequestResponse.SelectionContext.REQUEST);

        // Only show if we have the relevant message data
        byte[] messageBytes;
        String label;
        if (isResponse && reqResp.response() != null) {
            messageBytes = reqResp.response().toByteArray().getBytes();
            label = "response";
        } else if (isRequest && reqResp.request() != null) {
            messageBytes = reqResp.request().toByteArray().getBytes();
            label = "request";
        } else {
            return menuItems;
        }

        if (messageBytes.length == 0) return menuItems;

        String messageText = new String(messageBytes);

        // Extract pre-selection from Burp's editor
        String preSelection = null;
        int preSelOffset = -1;
        if (editorMsg.selectionOffsets().isPresent()) {
            burp.api.montoya.core.Range range = editorMsg.selectionOffsets().get();
            int start = range.startIndexInclusive();
            int end = range.endIndexExclusive();
            if (start >= 0 && end > start && end <= messageBytes.length) {
                preSelection = new String(messageBytes, start, end - start);
                preSelOffset = start;
            }
        }

        final String fPreSelection = preSelection;
        final int fPreSelOffset = preSelOffset;
        final String fLabel = label;

        JMenuItem autoRegexItem = new JMenuItem("Stepper-NG: Auto-Regex (" + label + ")");
        autoRegexItem.addActionListener(actionEvent -> {
            Component parent = Stepper.getUI() != null ? Stepper.getUI().getUiComponent() : null;
            AutoRegexDialog.Result result = AutoRegexDialog.show(
                    parent, messageText,
                    "Auto-Generate Regex — " + fLabel,
                    fLabel, fPreSelection, fPreSelOffset);

            if (result != null && !result.regex.isEmpty()) {
                // Offer to create a DVAR or copy
                DynamicGlobalVariableManager manager = Stepper.getDynamicGlobalVariableManager();
                if (manager != null) {
                    String varName = result.variableName.isEmpty()
                            ? "auto_" + System.currentTimeMillis()
                            : result.variableName;
                    manager.addVariable(new DynamicGlobalVariable(varName, result.regex, null));
                }
            }
        });
        menuItems.add(autoRegexItem);
        return menuItems;
    }
}
