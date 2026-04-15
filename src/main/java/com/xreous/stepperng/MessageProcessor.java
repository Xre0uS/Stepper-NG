package com.xreous.stepperng;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequencemanager.SequenceManager;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.util.ReplacingInputStream;
import com.xreous.stepperng.variable.StepVariable;

import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.RegexVariable;
import com.xreous.stepperng.variable.StaticGlobalVariable;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageProcessor implements HttpHandler {

    private final SequenceManager sequenceManager;
    private final Preferences preferences;
    private final DynamicGlobalVariableManager dynamicGlobalVariableManager;
    public static final String EXECUTE_BEFORE_HEADER = "X-Stepper-Execute-Before";
    public static final String EXECUTE_AFTER_HEADER = "X-Stepper-Execute-After";
    public static final String EXECUTE_VAR_HEADER = "X-Stepper-Argument";
    public static final String EXECUTE_BEFORE_REGEX = EXECUTE_BEFORE_HEADER + ":(.*)";
    public static final String EXECUTE_AFTER_REGEX = EXECUTE_AFTER_HEADER+":(.*)";
    public static final String EXECUTE_VAR_REGEX = EXECUTE_VAR_HEADER + ":(.*)";
    public static final String EXECUTE_AFTER_COMMENT_DELIMITER = "#%~%#";
    public static final Pattern EXECUTE_BEFORE_HEADER_PATTERN = Pattern.compile("^" + EXECUTE_BEFORE_REGEX + "$", Pattern.CASE_INSENSITIVE);
    public static final Pattern EXECUTE_AFTER_HEADER_PATTERN = Pattern.compile("^" + EXECUTE_AFTER_REGEX + "$", Pattern.CASE_INSENSITIVE);
    public static final Pattern EXECUTE_VAR_HEADER_PATTERN = Pattern.compile("^" + EXECUTE_VAR_REGEX + "$", Pattern.CASE_INSENSITIVE);
    public static final Pattern SEQUENCE_NAME_PATTERN = Pattern.compile("^([^:]+)(?::?)");
    public static final Pattern VARIABLE_LIST_PATTERN = Pattern.compile("[^:]+(:\\s*(?<variables>.+))?");
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("(?<key>[^=]+)=(?<value>[^;]+);?");
    public static final Pattern SINGLE_VARIABLE_PATTERN = Pattern.compile("(?<key>[^=]+)=(?<value>.*)");

    private static final Set<Thread> internalRequestThreads = Collections.synchronizedSet(new HashSet<>());
    private static final ThreadLocal<ArrayDeque<String>> executionStack = ThreadLocal.withInitial(ArrayDeque::new);
    private static final AtomicInteger requestCounter = new AtomicInteger(0);

    public static final Pattern CROSS_SEQ_VAR_PATTERN = Pattern.compile("\\$VAR:([^:$]+):([^$]+)\\$");

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private static final AtomicBoolean showingUnprocessableWarning = new AtomicBoolean(false);
    private static volatile long lastUnprocessableWarningDismissedAt = 0;
    private static final long UNPROCESSABLE_WARNING_COOLDOWN_MS = 30_000;

    public static void markInternalRequest() {
        internalRequestThreads.add(Thread.currentThread());
    }

    public static void unmarkInternalRequest() {
        internalRequestThreads.remove(Thread.currentThread());
    }

    public static boolean isInternalRequest() {
        return internalRequestThreads.contains(Thread.currentThread());
    }

    public static void pushSequence(String sequenceName) {
        executionStack.get().push(sequenceName);
    }

    public static void popSequence() {
        ArrayDeque<String> stack = executionStack.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) executionStack.remove();
    }

    public static boolean isSequenceOnStack(String sequenceName) {
        return executionStack.get().contains(sequenceName);
    }

    public static int getStackDepth() {
        return executionStack.get().size();
    }

    public static void cleanup() {
        internalRequestThreads.clear();
        requestCounter.set(0);
    }

    public static int incrementAndGetRequestCount() {
        return requestCounter.incrementAndGet();
    }

    public MessageProcessor(SequenceManager sequenceManager, Preferences preferences, DynamicGlobalVariableManager dynamicVarManager){
        this.sequenceManager = sequenceManager;
        this.preferences = preferences;
        this.dynamicGlobalVariableManager = dynamicVarManager;
    }

    private boolean getBoolPref(String key, boolean defaultValue) {
        if (preferences == null) return defaultValue;
        try {
            Object val = preferences.getSetting(key);
            return val instanceof Boolean b ? b : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static final byte[] VAR_MARKER = "$VAR:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DVAR_MARKER = "$DVAR:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GVAR_MARKER = "$GVAR:".getBytes(StandardCharsets.US_ASCII);

    public static boolean hasStepVariable(byte[] content) {
        return containsBytes(content, VAR_MARKER);
    }

    public static boolean hasStepVariable(String content) {
        return content.contains("$VAR:");
    }

    public static boolean hasDvarVariable(byte[] content) {
        return containsBytes(content, DVAR_MARKER);
    }

    public static boolean hasDvarVariable(String content) {
        return content.contains("$DVAR:");
    }

    public static boolean hasGvarVariable(byte[] content) {
        return containsBytes(content, GVAR_MARKER);
    }

    public static boolean hasGvarVariable(String content) {
        return content.contains("$GVAR:");
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        if (haystack.length < needle.length) return false;
        outer:
        for (int i = 0, limit = haystack.length - needle.length; i <= limit; i++) {
            if (haystack[i] == needle[0]) {
                for (int j = 1; j < needle.length; j++) {
                    if (haystack[i + j] != needle[j]) continue outer;
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isUnprocessable(byte[] content){
        return new String(content).indexOf('\uFFFD') != -1;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        HttpRequest request = requestToBeSent;
        Annotations annotations = requestToBeSent.annotations();

        if (isInternalRequest()) {
            return RequestToBeSentAction.continueWith(request, annotations);
        }

        if(isValidTool(requestToBeSent.toolSource())){
            List<String> headers = request.headers().stream().map(h -> h.name() + ": " + h.value()).toList();
            byte[] requestBytes = request.toByteArray().getBytes();

            Map<String, String> standaloneArgs = extractArgumentsFromHeaders(headers, EXECUTE_VAR_HEADER_PATTERN);

            Set<StepSequence> alreadyExecuted = new HashSet<>();

            List<RequestSequenceInformation> preExecSequences = extractExecSequencesFromHeaders(headers, EXECUTE_BEFORE_HEADER_PATTERN);
            if(!preExecSequences.isEmpty()){
                request = removeHeaderMatchingPattern(request, EXECUTE_BEFORE_HEADER_PATTERN);
                request = removeHeaderMatchingPattern(request, EXECUTE_VAR_HEADER_PATTERN);
                for (RequestSequenceInformation info : preExecSequences) {
                    Map<String, String> merged = new HashMap<>(standaloneArgs);
                    merged.putAll(info.arguments);
                    info.sequence.executeBlocking(merged);
                    alreadyExecuted.add(info.sequence);
                }
                requestBytes = request.toByteArray().getBytes();
            }

            List<RequestSequenceInformation> postExecSequences = extractExecSequencesFromHeaders(headers, EXECUTE_AFTER_HEADER_PATTERN);
            if(!postExecSequences.isEmpty()){
                request = removeHeaderMatchingPattern(request, EXECUTE_AFTER_HEADER_PATTERN);
                request = removeHeaderMatchingPattern(request, EXECUTE_VAR_HEADER_PATTERN);
                HashMap<String, Map<String, String>> serializedMap = new HashMap<>();
                for (RequestSequenceInformation info : postExecSequences) {
                    Map<String, String> merged = new HashMap<>(standaloneArgs);
                    merged.putAll(info.arguments);
                    serializedMap.put(info.sequence.getSequenceId(), merged);
                }
                String serialized = GSON.toJson(serializedMap);
                String existingNotes = annotations.notes() != null ? annotations.notes() : "";
                annotations = annotations.withNotes(existingNotes + EXECUTE_AFTER_HEADER + ":" + serialized);
                requestBytes = request.toByteArray().getBytes();
            }

            String requestString = new String(requestBytes);
            boolean hasDollar = requestString.indexOf('$') >= 0;

            if (dynamicGlobalVariableManager != null
                    && !dynamicGlobalVariableManager.getVariables().isEmpty()
                    && dynamicGlobalVariableManager.hasRequestCaptureDvars()) {
                try {
                    String host = request.httpService() != null ? request.httpService().host() : null;
                    dynamicGlobalVariableManager.processRequest(requestString, host);
                } catch (Exception e) {
                    try { Stepper.montoya.logging().logToError("Stepper-NG: DVAR request capture error: " + e.getMessage()); } catch (Exception ignored) {}
                }
            }

            if (hasDollar) {
                Set<StepSequence> autoExecSequences = sequenceManager.getSequencesToAutoExecute(requestString);
                if (!autoExecSequences.isEmpty()) {
                    int validateEveryN = 1;
                    if (preferences != null) {
                        try {
                            Object val = preferences.getSetting(Globals.PREF_SESSION_VALIDATE_EVERY_N);
                            if (val instanceof Integer i) validateEveryN = Math.max(1, i);
                        } catch (Exception e) {
                            try { Stepper.montoya.logging().logToError("Stepper-NG: Failed to read validate-every-N preference: " + e.getMessage()); } catch (Exception ignored) {}
                        }
                    }

                    boolean holdRequests = getBoolPref(Globals.PREF_HOLD_REQUESTS_DURING_EXECUTION, false);

                    int count = incrementAndGetRequestCount();
                    boolean shouldValidate = (count % validateEveryN == 0);

                    for (StepSequence seq : autoExecSequences) {
                        if (!alreadyExecuted.contains(seq)
                                && !isSequenceOnStack(seq.getSequenceId())
                                && getStackDepth() < Globals.MAX_SEQUENCE_DEPTH) {
                            if (!seq.isExecuting() && shouldValidate) {
                                seq.executeBlocking();
                            } else if (holdRequests && seq.isExecuting()
                                    && getStackDepth() == 0) {
                                seq.awaitExecution();
                            }
                        }
                    }
                }

                if (hasStepVariable(requestString)) {
                    HashMap<StepSequence, List<StepVariable>> allVariables = sequenceManager.getRollingVariablesFromAllSequences();
                    if(allVariables.size() > 0) {
                        if(isUnprocessable(requestBytes) && getBoolPref(Globals.PREF_ENABLE_UNPROCESSABLE_WARNING, true)){
                            if (System.currentTimeMillis() - lastUnprocessableWarningDismissedAt >= UNPROCESSABLE_WARNING_COOLDOWN_MS
                                    && showingUnprocessableWarning.compareAndSet(false, true)) {
                                final boolean[] proceed = {true};
                                try {
                                    SwingUtilities.invokeAndWait(() -> {
                                        try {
                                            String[] options = {"Yes", "No", "Don't show again"};
                                            int result = JOptionPane.showOptionDialog(Stepper.getUI().getUiComponent(),
                                                    "The request contains non-UTF-8 characters.\nStepper can make the replacements, " +
                                                            "but some binary data may be lost. Continue?",
                                                    "Stepper Replacement Warning", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                                                    null, options, options[0]);
                                            if (result == 1) {
                                                proceed[0] = false;
                                            } else if (result == 2) {
                                                if (preferences != null) preferences.setSetting(Globals.PREF_ENABLE_UNPROCESSABLE_WARNING, false);
                                            }
                                        } finally {
                                            lastUnprocessableWarningDismissedAt = System.currentTimeMillis();
                                            showingUnprocessableWarning.set(false);
                                        }
                                    });
                                } catch (Exception ignored) {
                                    lastUnprocessableWarningDismissedAt = System.currentTimeMillis();
                                    showingUnprocessableWarning.set(false);
                                }
                                if (!proceed[0])
                                    return RequestToBeSentAction.continueWith(request, annotations);
                            }
                        }

                        try {
                            requestBytes = makeReplacementsForAllSequences(requestBytes, allVariables);
                            if(getBoolPref(Globals.PREF_UPDATE_REQUEST_LENGTH, true)){
                                requestBytes = updateContentLength(requestBytes);
                            }
                        } catch (Exception e) {
                            Stepper.montoya.logging().logToError("Stepper-NG: Variable replacement error: " + e.getMessage());
                        }
                    }
                }

                if (hasDvarVariable(requestString)) {
                    try {
                        requestBytes = makeDvarReplacements(requestBytes, dynamicGlobalVariableManager);
                        if(getBoolPref(Globals.PREF_UPDATE_REQUEST_LENGTH, true)){
                            requestBytes = updateContentLength(requestBytes);
                        }
                    } catch (UnsupportedOperationException e) {
                        Stepper.montoya.logging().logToError("Stepper-NG: DVAR replacement error: " + e.getMessage());
                    }
                }

                if (hasGvarVariable(requestString)) {
                    try {
                        requestBytes = makeGvarReplacements(requestBytes, dynamicGlobalVariableManager);
                        if(getBoolPref(Globals.PREF_UPDATE_REQUEST_LENGTH, true)){
                            requestBytes = updateContentLength(requestBytes);
                        }
                    } catch (UnsupportedOperationException e) {
                        Stepper.montoya.logging().logToError("Stepper-NG: GVAR replacement error: " + e.getMessage());
                    }
                }
            }

            request = HttpRequest.httpRequest(request.httpService(), burp.api.montoya.core.ByteArray.byteArray(requestBytes));
        }

        return RequestToBeSentAction.continueWith(request, annotations);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        Annotations annotations = responseReceived.annotations();
        String notes = annotations.notes();
        if(notes != null && !notes.isEmpty()){
            List<RequestSequenceInformation> postExecSequences = extractExecSequencesFromComment(notes, EXECUTE_AFTER_HEADER_PATTERN);
            if(!postExecSequences.isEmpty()){
                for (RequestSequenceInformation info : postExecSequences) {
                    info.sequence.executeBlocking(info.arguments);
                }
                String cleanedNotes = notes.replaceAll(EXECUTE_AFTER_REGEX + ".*", "");
                annotations = annotations.withNotes(cleanedNotes);
            }
        }

        boolean needsDvar = dynamicGlobalVariableManager != null
                && !dynamicGlobalVariableManager.getVariables().isEmpty();
        boolean needsPassthrough = !isInternalRequest() && sequenceManager != null
                && sequenceManager.hasAnyPublishedRegexVariables();

        if (!needsDvar && !needsPassthrough) {
            return ResponseReceivedAction.continueWith(responseReceived, annotations);
        }

        // Lazily convert response to string only once, shared by DVAR and passthrough
        String responseText = null;

        if (needsDvar) {
            try {
                responseText = responseReceived.toString();
                String host = responseReceived.initiatingRequest() != null &&
                              responseReceived.initiatingRequest().httpService() != null ?
                              responseReceived.initiatingRequest().httpService().host() : null;
                dynamicGlobalVariableManager.processResponse(responseText, host);
            } catch (Exception e) {
                try { Stepper.montoya.logging().logToError("Stepper-NG: DVAR response capture error: " + e.getMessage()); } catch (Exception ignored) {}
            }
        }

        if (needsPassthrough) {
            try {
                if (responseText == null) {
                    responseText = new String(responseReceived.toByteArray().getBytes());
                }
                for (StepSequence seq : sequenceManager.getSequences()) {
                    if (seq.isDisabled()) continue;
                    boolean synced = false;
                    for (Step step : seq.getSteps()) {
                        for (StepVariable var : step.getVariableManager().getVariables()) {
                            if (var.isPublished() && var instanceof RegexVariable regexVar && regexVar.isValid()) {
                                String oldVal = var.getValue();
                                regexVar.updateFromResponse(responseText);
                                String newVal = var.getValue();
                                if (newVal != null && !newVal.isEmpty() && !newVal.equals(oldVal)) {
                                    synced = true;
                                }
                            }
                        }
                        if (synced) {
                            seq.syncVariableValues(step);
                        }
                    }
                }
            } catch (Exception e) {
                try { Stepper.montoya.logging().logToError("Stepper-NG passthrough error: " + e.getMessage()); } catch (Exception ignored) {}
            }
        }

        return ResponseReceivedAction.continueWith(responseReceived, annotations);
    }

    private boolean isValidTool(burp.api.montoya.core.ToolSource toolSource){
        if(preferences == null) return true;
        if(preferences.getSetting(Globals.PREF_VARS_IN_ALL_TOOLS)) return true;
        if(toolSource.isFromTool(ToolType.PROXY))
            return (boolean) preferences.getSetting(Globals.PREF_VARS_IN_PROXY);
        if(toolSource.isFromTool(ToolType.REPEATER))
            return (boolean) preferences.getSetting(Globals.PREF_VARS_IN_REPEATER);
        if(toolSource.isFromTool(ToolType.INTRUDER))
            return (boolean) preferences.getSetting(Globals.PREF_VARS_IN_INTRUDER);
        if(toolSource.isFromTool(ToolType.SCANNER))
            return (boolean) preferences.getSetting(Globals.PREF_VARS_IN_SCANNER);
        if(toolSource.isFromTool(ToolType.SEQUENCER))
            return (boolean) preferences.getSetting(Globals.PREF_VARS_IN_SEQUENCER);
        if(toolSource.isFromTool(ToolType.EXTENSIONS))
            return (boolean) preferences.getSetting(Globals.PREF_VARS_IN_EXTENDER);
        return false;
    }


    public static byte[] makeReplacementsForSingleSequence(byte[] originalContent, List<StepVariable> variables) {
        List<ReplacingInputStream.Replacement> replacements = new ArrayList<>();
        for (StepVariable variable : variables) {
            String match = StepVariable.createVariableString(variable.getIdentifier());
            String replace = variable.getValue() != null ? variable.getValue() : "";
            replacements.add(new ReplacingInputStream.Replacement(match, replace));
        }
        return ReplacingInputStream.applyReplacements(originalContent, replacements);
    }

    public static byte[] makeReplacementsForAllSequences(byte[] originalContent,
                                                         HashMap<StepSequence, List<StepVariable>> sequenceVariableMap) {
        List<ReplacingInputStream.Replacement> replacements = new ArrayList<>();
        for (Map.Entry<StepSequence, List<StepVariable>> sequenceEntry : sequenceVariableMap.entrySet()) {
            StepSequence sequence = sequenceEntry.getKey();
            List<StepVariable> variables = sequenceEntry.getValue();
            for (StepVariable variable : variables) {
                String match = StepVariable.createVariableString(sequence.getTitle(), variable.getIdentifier());
                String replace = variable.getValue() != null ? variable.getValue() : "";
                replacements.add(new ReplacingInputStream.Replacement(match, replace));
            }
        }
        return ReplacingInputStream.applyReplacements(originalContent, replacements);
    }

    public static byte[] updateContentLength(byte[] request){
        String requestStr = new String(request, StandardCharsets.UTF_8);
        int headerEnd = requestStr.indexOf("\r\n\r\n");
        if (headerEnd == -1) return request;

        String headerPart = requestStr.substring(0, headerEnd);
        String bodyPart = requestStr.substring(headerEnd + 4);
        int bodyLength = bodyPart.getBytes(StandardCharsets.UTF_8).length;

        String[] headerLines = headerPart.split("\r\n");
        StringBuilder newHeaders = new StringBuilder();
        boolean found = false;
        for (String line : headerLines) {
            if (line.toLowerCase().startsWith("content-length:")) {
                found = true;
                continue;
            }
            newHeaders.append(line).append("\r\n");
        }
        if (bodyLength > 0 || found) {
            newHeaders.append("Content-Length: ").append(bodyLength).append("\r\n");
        }
        newHeaders.append("\r\n").append(bodyPart);
        return newHeaders.toString().getBytes(StandardCharsets.UTF_8);
    }

    public List<RequestSequenceInformation> extractExecSequencesFromHeaders(List<String> headers, Pattern pattern){
        ArrayList<RequestSequenceInformation> execSequences = new ArrayList<>();
        for (String header : headers) {
            Matcher m = pattern.matcher(header);
            if (!m.matches()) continue;

            String headerValue = m.group(1).trim();
            Matcher nameMatcher = SEQUENCE_NAME_PATTERN.matcher(headerValue);
            if (!nameMatcher.find()) continue;

            String sequenceNameOrId = nameMatcher.group(1).trim();
            Map<String, String> arguments = extractVariablesFromInfoString(headerValue);

            Optional<StepSequence> execSequence = sequenceManager.findSequence(sequenceNameOrId);

            if (execSequence.isPresent())
                execSequences.add(new RequestSequenceInformation(execSequence.get(), arguments));
            else
                Stepper.montoya.logging().logToError("Stepper-NG: Could not find execution sequence: \"" + sequenceNameOrId + "\".");
        }
        return execSequences;
    }

    public Map<String, String> extractArgumentsFromHeaders(List<String> headers, Pattern pattern) {
        Map<String, String> arguments = new HashMap<>();
        for (String header : headers) {
            Matcher m = pattern.matcher(header);
            if (!m.matches()) continue;
            String variableInfo = m.group(1).trim();
            Matcher argMatcher = SINGLE_VARIABLE_PATTERN.matcher(variableInfo);
            if (argMatcher.matches()) {
                arguments.put(argMatcher.group("key").trim(), argMatcher.group("value").trim());
            }
        }
        return arguments;
    }

    private Map<String, String> extractVariablesFromInfoString(String infoString) {
        HashMap<String, String> map = new HashMap<>();
        Matcher listMatcher = VARIABLE_LIST_PATTERN.matcher(infoString);
        if (listMatcher.find() && listMatcher.group("variables") != null) {
            Matcher varMatcher = VARIABLE_PATTERN.matcher(listMatcher.group("variables"));
            while (varMatcher.find()) {
                map.put(varMatcher.group("key").trim(), varMatcher.group("value").trim());
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public List<RequestSequenceInformation> extractExecSequencesFromComment(String comment, Pattern pattern){
        ArrayList<RequestSequenceInformation> execSequences = new ArrayList<>();
        Matcher m = pattern.matcher(comment);
        if (m.find()) {
            String serialized = m.group(1);
            try {
                java.lang.reflect.Type mapType = new com.google.gson.reflect.TypeToken<HashMap<String, HashMap<String, String>>>(){}.getType();
                HashMap<String, HashMap<String, String>> allSequences = GSON.fromJson(serialized, mapType);
                if (allSequences != null) {
                    for (Map.Entry<String, HashMap<String, String>> entry : allSequences.entrySet()) {
                        String sequenceIdOrName = entry.getKey();
                        Optional<StepSequence> execSequence = sequenceManager.findSequence(sequenceIdOrName);
                        Map<String, String> variables = entry.getValue() != null ? entry.getValue() : new HashMap<>();
                        if (execSequence.isPresent())
                            execSequences.add(new RequestSequenceInformation(execSequence.get(), variables));
                        else
                            Stepper.montoya.logging().logToError("Stepper-NG: Could not find execution sequence: \"" + sequenceIdOrName + "\".");
                    }
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                String[] parts = serialized.split(EXECUTE_AFTER_COMMENT_DELIMITER);
                for (String sequenceNameOrId : parts) {
                    if (sequenceNameOrId == null || sequenceNameOrId.trim().isEmpty()) continue;
                    Optional<StepSequence> execSequence = sequenceManager.findSequence(sequenceNameOrId.trim());
                    if (execSequence.isPresent())
                        execSequences.add(new RequestSequenceInformation(execSequence.get(), new HashMap<>()));
                }
            }
        }
        return execSequences;
    }

    public static HttpRequest removeHeaderMatchingPattern(HttpRequest request, Pattern pattern){
        List<burp.api.montoya.http.message.HttpHeader> headers = request.headers();
        for (burp.api.montoya.http.message.HttpHeader header : headers) {
            String headerLine = header.name() + ": " + header.value();
            if(pattern.matcher(headerLine).matches()){
                request = request.withRemovedHeader(header.name());
            }
        }
        return request;
    }

    public static byte[] makeDvarReplacements(byte[] originalContent, DynamicGlobalVariableManager manager) {
        List<ReplacingInputStream.Replacement> replacements = new ArrayList<>();
        for (DynamicGlobalVariable dvar : manager.getVariables()) {
            if (dvar.getValue() != null) {
                String match = DynamicGlobalVariable.createDvarString(dvar.getIdentifier());
                String replace = dvar.getValue();
                replacements.add(new ReplacingInputStream.Replacement(match, replace));
            }
        }
        if (replacements.isEmpty()) return originalContent;
        return ReplacingInputStream.applyReplacements(originalContent, replacements);
    }

    public static byte[] makeGvarReplacements(byte[] originalContent, DynamicGlobalVariableManager manager) {
        List<ReplacingInputStream.Replacement> replacements = new ArrayList<>();
        for (StaticGlobalVariable svar : manager.getStaticVariables()) {
            if (svar.getValue() != null) {
                String match = StaticGlobalVariable.createGvarString(svar.getIdentifier());
                String replace = svar.getValue();
                replacements.add(new ReplacingInputStream.Replacement(match, replace));
            }
        }
        if (replacements.isEmpty()) return originalContent;
        return ReplacingInputStream.applyReplacements(originalContent, replacements);
    }
}
