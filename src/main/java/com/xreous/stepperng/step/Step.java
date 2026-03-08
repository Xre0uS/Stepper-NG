package com.xreous.stepperng.step;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.xreous.stepperng.Globals;
import com.xreous.stepperng.MessageProcessor;
import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.condition.StepCondition;
import com.xreous.stepperng.exception.SequenceExecutionException;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.step.listener.StepExecutionListener;
import com.xreous.stepperng.variable.StepVariable;

import java.util.*;

public class Step {

    private final List<StepExecutionListener> executionListeners;
    private final StepVariableManager variableManager;
    private StepSequence sequence;
    private StepExecutionInfo lastExecutionInfo;
    private String hostname;
    private Integer port;
    private Boolean isSSL;
    private String title;
    private boolean enabled = true;
    private StepCondition condition;
    private String lastConditionResult;
    private long lastExecutionTime;

    private byte[] requestBody;
    private byte[] responseBody;

    public Step(){
        this.variableManager = new StepVariableManager(this);
        this.executionListeners = new ArrayList<>();
        this.requestBody = new byte[0];
        this.responseBody = new byte[0];
        this.hostname = "";
        this.port = 443;
        this.isSSL = true;
    }

    public Step(StepSequence sequence, String title){
        this();
        this.sequence = sequence;
        if(title != null) {
            this.title = title;
        }else{
            this.title = "Step " + (sequence.getSteps().size()+1);
        }
    }

    public Step(StepSequence sequence){
        this(sequence, null);
    }

    public void setSequence(StepSequence sequence) {
        this.sequence = sequence;
    }

    public void setRequestBody(byte[] requestBody){
        this.requestBody = requestBody;
    }

    public void setResponseBody(byte[] responseBody){
        this.responseBody = responseBody;
    }

    public StepVariableManager getVariableManager() {
        return variableManager;
    }

    public StepSequence getSequence() {
        return sequence;
    }

    public byte[] getRequest() {
        return this.requestBody;
    }

    public byte[] getResponse() {
        return this.responseBody;
    }

    public HttpService getHttpService() {
        if(this.hostname == null || this.hostname.isEmpty()) return null;
        int p = this.port != null ? this.port : 443;
        boolean ssl = this.isSSL != null ? this.isSSL : true;
        return HttpService.httpService(this.hostname, p, ssl);
    }

    public StepExecutionInfo executeStep() throws SequenceExecutionException {
        List<StepVariable> variables = this.sequence.getRollingVariablesUpToStep(this);
        return this.executeStep(variables);
    }

    public StepExecutionInfo executeStep(List<StepVariable> replacements) throws SequenceExecutionException {
        byte[] requestWithoutReplacements = getRequest();
        if (requestWithoutReplacements == null || requestWithoutReplacements.length == 0) {
            throw new SequenceExecutionException("No request data. Add a request first.");
        }
        byte[] builtRequest;

        this.variableManager.updateVariablesBeforeExecution();

        for (StepExecutionListener executionListener : this.executionListeners) {
            executionListener.beforeStepExecution();
        }

        if(MessageProcessor.hasStepVariable(requestWithoutReplacements)) {
            builtRequest = MessageProcessor.makeReplacementsForSingleSequence(requestWithoutReplacements, replacements);
            HashMap<StepSequence, List<StepVariable>> allVariables = Stepper.getSequenceManager().getRollingVariablesFromAllSequences();
            builtRequest = MessageProcessor.makeReplacementsForAllSequences(builtRequest, allVariables);
        }else{
            builtRequest = Arrays.copyOf(requestWithoutReplacements, requestWithoutReplacements.length);
        }

        if (MessageProcessor.hasDvarVariable(builtRequest) && Stepper.getDynamicGlobalVariableManager() != null) {
            builtRequest = MessageProcessor.makeDvarReplacements(builtRequest, Stepper.getDynamicGlobalVariableManager());
        }

        if (MessageProcessor.hasGvarVariable(builtRequest) && Stepper.getDynamicGlobalVariableManager() != null) {
            builtRequest = MessageProcessor.makeGvarReplacements(builtRequest, Stepper.getDynamicGlobalVariableManager());
        }

        if(Stepper.getPreferences() != null && (boolean) Stepper.getPreferences().getSetting(Globals.PREF_UPDATE_REQUEST_LENGTH)){
            builtRequest = MessageProcessor.updateContentLength(builtRequest);
        }

        setResponseBody(new byte[0]);

        String host = this.hostname;
        int port = this.port != null ? this.port : 443;
        boolean ssl = this.isSSL != null ? this.isSSL : true;

        if (host == null || host.isEmpty()) {
            String requestStr = new String(builtRequest, java.nio.charset.StandardCharsets.UTF_8);
            for (String line : requestStr.split("\r\n")) {
                if (line.toLowerCase().startsWith("host:")) {
                    String hostValue = line.substring(5).trim();
                    if (hostValue.contains(":")) {
                        String[] parts = hostValue.split(":", 2);
                        host = parts[0];
                        try { port = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                    } else {
                        host = hostValue;
                    }
                    break;
                }
            }
        }

        if (host == null || host.isEmpty()) {
            throw new SequenceExecutionException("No target host specified. Edit the target or include a Host header in the request.");
        }

        HttpService httpService = HttpService.httpService(host, port, ssl);

        long start = System.currentTimeMillis();
        HttpRequestResponse requestResponse = null;
        try {
            HttpRequest httpRequest = HttpRequest.httpRequest(httpService, burp.api.montoya.core.ByteArray.byteArray(builtRequest));
            MessageProcessor.markInternalRequest();
            try {
                requestResponse = Stepper.montoya.http().sendRequest(httpRequest);
            } finally {
                MessageProcessor.unmarkInternalRequest();
            }
        }catch (RuntimeException e){
            String msg = e.getMessage();
            if(msg == null || msg.isEmpty() || msg.equalsIgnoreCase(host))
                throw new SequenceExecutionException(String.format("Failed to execute step \"%s\": %s", this.title, msg));
            throw new SequenceExecutionException(e.getMessage());
        }
        long end = System.currentTimeMillis();
        if(requestResponse == null || requestResponse.response() == null)
            throw new SequenceExecutionException("The request to the server timed out.");

        setResponseBody(requestResponse.response().toByteArray().getBytes());

        this.lastExecutionInfo = new StepExecutionInfo(this, requestResponse, end-start);

        this.variableManager.updateVariablesAfterExecution(lastExecutionInfo);

        if (this.sequence != null) {
            this.sequence.syncVariableValues(this);
        }

        for (StepExecutionListener executionListener : executionListeners) {
            executionListener.stepExecuted(lastExecutionInfo);
        }

        return lastExecutionInfo;
    }

    public void addExecutionListener(StepExecutionListener listener){
        this.executionListeners.add(listener);
    }

    public void removeExecutionListener(StepExecutionListener listener){
        this.executionListeners.remove(listener);
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public boolean isSSL() {
        return isSSL != null ? isSSL : true;
    }

    public void setSSL(boolean SSL) {
        isSSL = SSL;
    }

    public String getTargetString(){
        if(hostname == null || hostname.isEmpty()) return "Not specified";
        boolean ssl = isSSL != null ? isSSL : true;
        int p = port != null ? port : (ssl ? 443 : 80);
        return "http" + (ssl ? "s" : "") + "://" + hostname + (p != 80 && p != 443 ? ":" + p : "");
    }

    public boolean isValidTarget(){
        return this.hostname != null && !this.hostname.isEmpty() && this.port != null && this.isSSL != null;
    }

    public boolean isReadyToExecute(){
        if (this.getRequest() == null || this.getRequest().length == 0) return false;
        if (this.isValidTarget()) return true;
        String requestStr = new String(this.getRequest(), java.nio.charset.StandardCharsets.UTF_8);
        for (String line : requestStr.split("\r\n")) {
            if (line.toLowerCase().startsWith("host:") && !line.substring(5).trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void setHttpService(HttpService httpService) {
        this.hostname = httpService.host();
        this.port = httpService.port();
        this.isSSL = httpService.secure();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public StepCondition getCondition() {
        return condition;
    }

    public void setCondition(StepCondition condition) {
        this.condition = condition;
    }

    public StepExecutionInfo getLastExecutionResult() {
        return this.lastExecutionInfo;
    }

    public String getLastConditionResult() { return lastConditionResult; }
    public void setLastConditionResult(String lastConditionResult) { this.lastConditionResult = lastConditionResult; }
    public long getLastExecutionTime() { return lastExecutionTime; }
    public void setLastExecutionTime(long lastExecutionTime) { this.lastExecutionTime = lastExecutionTime; }
}
