package com.xreous.stepperng.step.view;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.coreyd97.BurpExtenderUtilities.Alignment;
import com.coreyd97.BurpExtenderUtilities.PanelBuilder;
import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.condition.view.StepConditionPanel;
import com.xreous.stepperng.exception.SequenceCancelledException;
import com.xreous.stepperng.sequence.view.SequenceContainer;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.StepExecutionInfo;
import com.xreous.stepperng.step.listener.StepExecutionAdapter;
import com.xreous.stepperng.util.Utils;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.listener.StepVariableListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class StepPanel extends JPanel implements StepVariableListener {

    private final SequenceContainer sequenceContainer;
    private final Step step;

    private HttpRequestEditor requestEditor;
    private HttpResponseEditor responseEditor;
    private JSplitPane reqRespSplitPane;
    private JLabel responseLengthLabel;
    private JLabel responseTimeLabel;

    private JLabel targetLabel;
    private StepConditionPanel conditionPanel;

    public StepPanel(SequenceContainer sequenceContainer, Step step){
        super(new BorderLayout());
        this.sequenceContainer = sequenceContainer;
        this.step = step;

        this.requestEditor = Stepper.montoya.userInterface().createHttpRequestEditor();
        if(step.getRequest() != null && step.getRequest().length > 0) {
            this.requestEditor.setRequest(HttpRequest.httpRequest(ByteArray.byteArray(step.getRequest())));
        }
        this.responseEditor = Stepper.montoya.userInterface().createHttpResponseEditor();
        if(step.getResponse() != null && step.getResponse().length > 0) {
            this.responseEditor.setResponse(HttpResponse.httpResponse(ByteArray.byteArray(step.getResponse())));
        }

        JPanel responseWrapper = new JPanel(new BorderLayout());
        JPanel responseInfoWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        responseInfoWrapper.add(new JLabel("Response Length: "));
        responseLengthLabel = new JLabel("N/A");
        responseInfoWrapper.add(responseLengthLabel);
        responseInfoWrapper.add(new JLabel(" |  Response Time: "));
        responseTimeLabel = new JLabel("N/A");
        responseInfoWrapper.add(responseTimeLabel);

        this.step.addExecutionListener(new StepExecutionAdapter(){
            @Override
            public void stepExecuted(StepExecutionInfo executionInfo) {
                SwingUtilities.invokeLater(() -> {
                    if(executionInfo.getRequestResponse().response() != null) {
                        responseEditor.setResponse(executionInfo.getRequestResponse().response());
                        String lengthMsg = String.format("%d bytes", executionInfo.getRequestResponse().response().toByteArray().length());
                        StepPanel.this.responseLengthLabel.setText(lengthMsg);
                    }
                    String timeMsg = String.format("%d ms", executionInfo.getResponseTime());
                    StepPanel.this.responseTimeLabel.setText(timeMsg);
                });
            }
        });

        responseWrapper.add(responseEditor.uiComponent(), BorderLayout.CENTER);
        responseWrapper.add(responseInfoWrapper, BorderLayout.SOUTH);

        VariablePanel preExecVariablePanel = new PreExecVariablePanel(step.getVariableManager(), step);
        JSplitPane requestSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                requestEditor.uiComponent(), preExecVariablePanel);
        requestSplitPane.setResizeWeight(0.8);

        VariablePanel postExecVariablePanel = new PostExecVariablePanel(step.getVariableManager(), step, responseEditor);
        JSplitPane responseSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                responseWrapper, postExecVariablePanel);
        responseSplitPane.setResizeWeight(0.8);

        requestSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
                new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent pce) {
                        responseSplitPane.setDividerLocation(requestSplitPane.getDividerLocation());
                    }
                });
        responseSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
                new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent pce) {
                        requestSplitPane.setDividerLocation(responseSplitPane.getDividerLocation());
                    }
                });

        this.reqRespSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestSplitPane, responseSplitPane);
        this.reqRespSplitPane.setResizeWeight(0.5);

        this.reqRespSplitPane.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && reqRespSplitPane.isShowing() && reqRespSplitPane.getWidth() > 0) {
                SwingUtilities.invokeLater(() -> reqRespSplitPane.setDividerLocation(0.5));
            }
        });

        JButton executeStepButton = new JButton("Execute Step");
        executeStepButton.setMargin(new Insets(7,7,7,7));
        executeStepButton.addActionListener(actionEvent -> {
            Thread t = new Thread(() -> {
                executeStepButton.setEnabled(false);
                try {
                    byte[] currentRequest = getRequestEditor().getMessage();
                    if (currentRequest != null && currentRequest.length > 0) {
                        step.setRequestBody(currentRequest);
                    }
                    step.executeStep();
                    step.setLastExecutionTime(System.currentTimeMillis());
                    if (step.getSequence() != null) {
                        step.getSequence().stepModified(step);
                    }
                }catch (SequenceCancelledException ignored){
                }catch (Exception e){
                    String msg = e.getMessage();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(StepPanel.this, msg,
                            "Step Error", JOptionPane.ERROR_MESSAGE));
                }finally {
                    executeStepButton.setEnabled(true);
                }
            });
            t.setDaemon(true);
            t.setName("Stepper-NG-StepExec");
            t.start();
        });

        JButton editTargetButton = new JButton("Edit");

        Runnable setEditButtonIcon = () -> {
            boolean isDark = isDarkTheme();
            String editIconURI = isDark ? "resources/Media/dark/edit.png" : "resources/Media/edit.png";
            ImageIcon editIcon = Utils.loadImage(editIconURI, 15, 15);
            editTargetButton.setIcon(editIcon);
            if(editIcon != null) {
                editTargetButton.setText(null);
            }else{
                editTargetButton.setText("Edit");
            }
        };
        setEditButtonIcon.run();
        editTargetButton.addPropertyChangeListener("UI", propertyChangeEvent -> setEditButtonIcon.run());

        editTargetButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showHttpDialog();
            }
        });

        targetLabel = new JLabel(step.getTargetString());
        targetLabel.setBorder(BorderFactory.createEmptyBorder(0,10,0,10));

        JSeparator horizontalSeparator = new JSeparator(JSeparator.HORIZONTAL);
        conditionPanel = new StepConditionPanel(step);
        PanelBuilder panelBuilder = new PanelBuilder();
        panelBuilder.setComponentGrid(new Component[][]{
                new Component[]{executeStepButton, new JLabel("Target:", SwingConstants.TRAILING), targetLabel, editTargetButton},
                new Component[]{horizontalSeparator, horizontalSeparator, horizontalSeparator, horizontalSeparator},
                new Component[]{conditionPanel, conditionPanel, conditionPanel, conditionPanel},
                new Component[]{reqRespSplitPane, reqRespSplitPane, reqRespSplitPane, reqRespSplitPane},
        });
        panelBuilder.setGridWeightsX(new int[][]{
                new int[]{0, 1, 0, 0},
                new int[]{0, 0, 0, 0},
                new int[]{0, 0, 0, 0},
                new int[]{0, 0, 0, 0}
        });
        panelBuilder.setGridWeightsY(new int[][]{
                new int[]{0, 0, 0, 0},
                new int[]{0, 0, 0, 0},
                new int[]{0, 0, 0, 0},
                new int[]{1, 1, 1, 1}
        });
        panelBuilder.setAlignment(Alignment.FILL);

        JPanel builtPanel = panelBuilder.build();
        this.add(builtPanel, BorderLayout.CENTER);

        this.revalidate();
        this.repaint();
    }

    private void showHttpDialog(){
        JTextField httpAddressField = new JTextField();
        httpAddressField.setText(step.getHostname());
        int currentPort = step.getPort() != null ? step.getPort() : 443;
        JSpinner httpPortSpinner = new JSpinner(new SpinnerNumberModel(currentPort,1,65535,1));
        httpPortSpinner.setEditor(new JSpinner.NumberEditor(httpPortSpinner,"#"));
        JCheckBox httpIsSecure = new JCheckBox("Is HTTPS?");
        httpIsSecure.setSelected(step.isSSL());
        JLabel info = new JLabel("Specify the details of the server to which the request will be sent.");

        PanelBuilder panelBuilder = new PanelBuilder();
        panelBuilder.setAlignment(Alignment.FILL);
        panelBuilder.setComponentGrid(new Component[][]{
                new Component[]{info, info},
                new Component[]{new JLabel("Host"), httpAddressField},
                new Component[]{new JLabel("Port"), httpPortSpinner},
                new Component[]{httpIsSecure, null},
        });

        int answer = JOptionPane.showConfirmDialog(this, panelBuilder.build(), "HTTP Configuration", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if(answer == JOptionPane.YES_OPTION){
            step.setHostname(httpAddressField.getText());
            step.setPort((Integer) httpPortSpinner.getValue());
            step.setSSL(httpIsSecure.isSelected());
            targetLabel.setText(step.getTargetString());
        }
    }

    /**
     * Returns the raw request bytes from the editor.
     */
    public RequestEditorWrapper getRequestEditor() {
        return new RequestEditorWrapper(requestEditor);
    }

    public void refreshRequestPanel() {
        if(this.step.getRequest() != null && this.step.getRequest().length > 0) {
            this.requestEditor.setRequest(HttpRequest.httpRequest(ByteArray.byteArray(this.step.getRequest())));
        }
    }

    public void refreshValidationState() {
        if (conditionPanel != null) conditionPanel.refreshValidationState();
    }

    public void refreshStepCombos() {
        if (conditionPanel != null) conditionPanel.refreshStepCombos();
    }

    public Step getStep() {
        return step;
    }

    @Override
    public void onVariableAdded(StepVariable variable) {
        SwingUtilities.invokeLater(() -> this.sequenceContainer.updateSubsequentPanels(this));
    }

    @Override
    public void onVariableRemoved(StepVariable variable) {
        SwingUtilities.invokeLater(() -> this.sequenceContainer.updateSubsequentPanels(this));
    }

    @Override
    public void onVariableChange(StepVariable variable) {
        SwingUtilities.invokeLater(() -> this.sequenceContainer.updateSubsequentPanels(this));
    }

    /**
     * Wrapper to provide a getMessage()-like interface for the Montoya HttpRequestEditor.
     */
    public static class RequestEditorWrapper {
        private final HttpRequestEditor editor;
        public RequestEditorWrapper(HttpRequestEditor editor) {
            this.editor = editor;
        }
        public byte[] getMessage() {
            HttpRequest request = editor.getRequest();
            if(request == null) return new byte[0];
            return request.toByteArray().getBytes();
        }
    }

    private static boolean isDarkTheme() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0;
        return brightness < 0.5;
    }
}
