package com.xreous.stepperng.step.view;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.condition.view.StepConditionPanel;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.step.StepExecutionInfo;
import com.xreous.stepperng.step.listener.StepExecutionAdapter;
import com.xreous.stepperng.util.view.SplitPaneDoubleClick;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.listener.StepVariableListener;
import com.xreous.stepperng.step.listener.StepExecutionListener;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.Consumer;
/** Tree-mode step editor: condition strip + titled Request/Response + variable panels. */
public class StepPanel extends JPanel implements StepVariableListener {

    /** Identifies which divider was double-clicked so the host can broadcast to siblings. */
    public enum SplitKind { EDITOR, VARS, RIGHT }

    private final Consumer<StepPanel> onSubsequentRefresh;
    private final Consumer<SplitKind> dividerSync;
    private final Step step;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;
    private final JLabel responseLengthLabel;
    private final JLabel responseTimeLabel;
    private final StepConditionPanel conditionPanel;

    private final StepExecutionListener executionListener;
    private final StepVariableListener variableListener;

    private final JSplitPane editorSplit;
    private final JSplitPane varsSplit;
    private final JSplitPane rightSplit;

    public StepPanel(Step step, Consumer<StepPanel> onSubsequentRefresh){
        this(step, onSubsequentRefresh, null);
    }

    public StepPanel(Step step, Consumer<StepPanel> onSubsequentRefresh, Consumer<SplitKind> dividerSync){
        super(new BorderLayout());
        this.step = step;
        this.onSubsequentRefresh = onSubsequentRefresh != null ? onSubsequentRefresh : p -> {};
        this.dividerSync = dividerSync != null ? dividerSync : k -> applySplit(k);

        this.requestEditor = Stepper.montoya.userInterface().createHttpRequestEditor();
        if(step.getRequest() != null && step.getRequest().length > 0) {
            this.requestEditor.setRequest(HttpRequest.httpRequest(ByteArray.byteArray(step.getRequest())));
        }
        this.responseEditor = Stepper.montoya.userInterface().createHttpResponseEditor();
        if(step.getResponse() != null && step.getResponse().length > 0) {
            this.responseEditor.setResponse(HttpResponse.httpResponse(ByteArray.byteArray(step.getResponse())));
        }

        this.responseLengthLabel = new JLabel("N/A");
        this.responseTimeLabel = new JLabel("N/A");
        JPanel responseInfo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        responseInfo.add(new JLabel("Length:"));
        responseInfo.add(responseLengthLabel);
        responseInfo.add(new JLabel(" \u00B7 Time:"));
        responseInfo.add(responseTimeLabel);

        JPanel responseWrapper = new JPanel(new BorderLayout());
        responseWrapper.add(responseEditor.uiComponent(), BorderLayout.CENTER);
        responseWrapper.add(responseInfo, BorderLayout.SOUTH);

        this.executionListener = new StepExecutionAdapter(){
            @Override public void stepExecuted(StepExecutionInfo executionInfo) {
                SwingUtilities.invokeLater(() -> {
                    if(executionInfo.getRequestResponse().response() != null) {
                        responseEditor.setResponse(executionInfo.getRequestResponse().response());
                        responseLengthLabel.setText(String.format("%d bytes",
                                executionInfo.getRequestResponse().response().toByteArray().length()));
                    }
                    responseTimeLabel.setText(String.format("%d ms", executionInfo.getResponseTime()));
                });
            }
        };
        this.step.addExecutionListener(this.executionListener);

        JSplitPane editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("Request",  requestEditor.uiComponent()),
                wrapTitled("Response", responseWrapper));
        editorSplit.setResizeWeight(0.5);
        editorSplit.setContinuousLayout(true);
        editorSplit.setBorder(null);
        this.editorSplit = editorSplit;
        primeRatio(editorSplit, 0.5);
        SplitPaneDoubleClick.install(editorSplit, () -> this.dividerSync.accept(SplitKind.EDITOR));

        VariablePanel preExecVariablePanel  = new PreExecVariablePanel(step.getVariableManager(), step);
        VariablePanel postExecVariablePanel = new PostExecVariablePanel(step.getVariableManager(), step, responseEditor);
        JSplitPane varsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("Pre-execution variables",  preExecVariablePanel),
                wrapTitled("Post-execution variables", postExecVariablePanel));
        varsSplit.setResizeWeight(0.5);
        varsSplit.setContinuousLayout(true);
        varsSplit.setBorder(null);
        this.varsSplit = varsSplit;
        primeRatio(varsSplit, 0.5);
        SplitPaneDoubleClick.install(varsSplit, () -> this.dividerSync.accept(SplitKind.VARS));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorSplit, varsSplit);
        rightSplit.setResizeWeight(1.0);
        rightSplit.setContinuousLayout(true);
        rightSplit.setBorder(null);
        this.rightSplit = rightSplit;
        primeVarsHeight(rightSplit, step);
        SplitPaneDoubleClick.install(rightSplit, () -> this.dividerSync.accept(SplitKind.RIGHT));

        this.variableListener = new StepVariableListener() {
            @Override public void onVariableAdded(StepVariable variable)   { SwingUtilities.invokeLater(() -> reflowVarsHeight()); }
            @Override public void onVariableRemoved(StepVariable variable) { SwingUtilities.invokeLater(() -> reflowVarsHeight()); }
            @Override public void onVariableChange(StepVariable variable)  {}
        };
        step.getVariableManager().addVariableListener(this.variableListener);

        this.conditionPanel = new StepConditionPanel(step);

        JPanel body = new JPanel(new BorderLayout(0, 4));
        body.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        body.add(conditionPanel, BorderLayout.NORTH);
        body.add(rightSplit, BorderLayout.CENTER);
        this.add(body, BorderLayout.CENTER);
    }

    private static JComponent wrapTitled(String title, Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static void primeRatio(JSplitPane split, double ratio) {
        split.addComponentListener(new ComponentAdapter() {
            boolean done = false;
            @Override public void componentResized(ComponentEvent e) {
                if (done) return;
                int extent = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                        ? split.getWidth() : split.getHeight();
                if (extent <= 0) return;
                done = true;
                SwingUtilities.invokeLater(() -> split.setDividerLocation(ratio));
            }
        });
    }

    private static void primeVarsHeight(JSplitPane rightSplit, Step step) {
        rightSplit.addComponentListener(new ComponentAdapter() {
            boolean done = false;
            @Override public void componentResized(ComponentEvent e) {
                if (done || rightSplit.getHeight() <= 0) return;
                done = true;
                SwingUtilities.invokeLater(() -> applyVarsHeight(rightSplit, step));
            }
        });
    }

    private static void applyVarsHeight(JSplitPane rightSplit, Step step) {
        int h = rightSplit.getHeight();
        if (h <= 0) return;
        int preCount  = step.getVariableManager().getPreExecutionVariables().size();
        int postCount = step.getVariableManager().getPostExecutionVariables().size();
        int rows = Math.max(3, Math.max(preCount, postCount));
        int needed = rows * 24 + 140;
        int varsTarget = Math.min((int) (h * 0.66), Math.max(180, needed));
        rightSplit.setDividerLocation(Math.max(0, h - varsTarget));
    }

    private void reflowVarsHeight() {
        if (rightSplit == null || rightSplit.getHeight() <= 0) return;
        applyVarsHeight(rightSplit, step);
    }

    /** Grow the bottom (variables) pane to display every pre/post row, leaving editors a 100px floor. */
    private void fitVariablesShowAll() {
        if (rightSplit == null) return;
        int h = rightSplit.getHeight();
        if (h <= 0) return;
        int preCount  = step.getVariableManager().getPreExecutionVariables().size();
        int postCount = step.getVariableManager().getPostExecutionVariables().size();
        int rows = Math.max(3, Math.max(preCount, postCount));
        int needed = rows * 24 + 140;
        int varsTarget = Math.max(180, Math.min(needed, h - 100));
        rightSplit.setDividerLocation(Math.max(0, h - varsTarget));
    }

    /** Apply a divider reset for one split kind; invoked by the host across every step panel. */
    public void applySplit(SplitKind kind) {
        if (kind == null) return;
        switch (kind) {
            case EDITOR -> { if (editorSplit != null && editorSplit.getWidth()  > 0) editorSplit.setDividerLocation(0.5d); }
            case VARS   -> { if (varsSplit   != null && varsSplit.getWidth()    > 0) varsSplit.setDividerLocation(0.5d); }
            case RIGHT  -> fitVariablesShowAll();
        }
    }

    public RequestEditorWrapper getRequestEditor() { return new RequestEditorWrapper(requestEditor); }
    public void refreshRequestPanel() {
        if(this.step.getRequest() != null && this.step.getRequest().length > 0) {
            this.requestEditor.setRequest(HttpRequest.httpRequest(ByteArray.byteArray(this.step.getRequest())));
        }
    }
    public void refreshValidationState() { if (conditionPanel != null) conditionPanel.refreshValidationState(); }
    public void refreshStepCombos()      { if (conditionPanel != null) conditionPanel.refreshStepCombos(); }
    public Step getStep() { return step; }

    /** Unhooks listeners so the panel and its closures can be GC'd after removal. */
    public void dispose() {
        try { step.removeExecutionListener(executionListener); } catch (Exception ignored) {}
        try { step.getVariableManager().removeVariableListener(variableListener); } catch (Exception ignored) {}
    }

    @Override public void onVariableAdded(StepVariable variable)   { SwingUtilities.invokeLater(() -> onSubsequentRefresh.accept(this)); }
    @Override public void onVariableRemoved(StepVariable variable) { SwingUtilities.invokeLater(() -> onSubsequentRefresh.accept(this)); }
    @Override public void onVariableChange(StepVariable variable)  { SwingUtilities.invokeLater(() -> onSubsequentRefresh.accept(this)); }

    public static class RequestEditorWrapper {
        private final HttpRequestEditor editor;
        public RequestEditorWrapper(HttpRequestEditor editor) { this.editor = editor; }
        public byte[] getMessage() {
            HttpRequest request = editor.getRequest();
            if(request == null) return new byte[0];
            return request.toByteArray().getBytes();
        }
    }
}
