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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
    public static final String STEPPER_IGNORE_HEADER = "X-Stepper-Ignore";
    public static final Pattern STEPPER_IGNORE_PATTERN = Pattern.compile("^"+STEPPER_IGNORE_HEADER, Pattern.CASE_INSENSITIVE);
    public static final Pattern SEQUENCE_NAME_PATTERN = Pattern.compile("^([^:]+)(?::?)");
    public static final Pattern VARIABLE_LIST_PATTERN = Pattern.compile("[^:]+(:\\s*(?<variables>.+))?");
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("(?<key>[^=]+)=(?<value>[^;]+);?");
    public static final Pattern SINGLE_VARIABLE_PATTERN = Pattern.compile("(?<key>[^=]+)=(?<value>.*)");

    // Thread-local execution stack for infinite loop prevention
    private static final Set<Thread> internalRequestThreads = Collections.synchronizedSet(new HashSet<>());
    private static final ThreadLocal<ArrayDeque<String>> executionStack = ThreadLocal.withInitial(ArrayDeque::new);
    private final java.util.concurrent.atomic.AtomicInteger requestCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    // Pre-compiled patterns for hot-path checks — avoids Pattern.compile on every request
    private static final Pattern STEP_VARIABLE_PATTERN = Pattern.compile(Pattern.quote("$VAR:") + "(.*?)" + Pattern.quote("$"));
    private static final Pattern DVAR_VARIABLE_PATTERN = Pattern.compile("\\$DVAR:([^$]+)\\$");
    private static final Pattern GVAR_VARIABLE_PATTERN = Pattern.compile("\\$GVAR:([^$]+)\\$");
    public static final Pattern CROSS_SEQ_VAR_PATTERN = Pattern.compile("\\$VAR:([^:$]+):([^$]+)\\$");

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
    }

    public static boolean isSequenceOnStack(String sequenceName) {
        return executionStack.get().contains(sequenceName);
    }

    public static int getStackDepth() {
        return executionStack.get().size();
    }

    public MessageProcessor(SequenceManager sequenceManager, Preferences preferences, DynamicGlobalVariableManager dynamicVarManager){
        this.sequenceManager = sequenceManager;
        this.preferences = preferences;
        this.dynamicGlobalVariableManager = dynamicVarManager;
    }

    public static boolean hasStepVariable(byte[] content) {
        return hasStepVariable(new String(content));
    }

    public static boolean hasStepVariable(String content) {
        return STEP_VARIABLE_PATTERN.matcher(content).find();
    }

    public static boolean hasDvarVariable(byte[] content) {
        return hasDvarVariable(new String(content));
    }

    public static boolean hasDvarVariable(String content) {
        return DVAR_VARIABLE_PATTERN.matcher(content).find();
    }

    public static boolean hasGvarVariable(byte[] content) {
        return hasGvarVariable(new String(content));
    }

    public static boolean hasGvarVariable(String content) {
        return GVAR_VARIABLE_PATTERN.matcher(content).find();
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
                // Re-read bytes after header removal
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
                    serializedMap.put(info.sequence.getTitle(), merged);
                }
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String serialized = gson.toJson(serializedMap);
                String existingNotes = annotations.notes() != null ? annotations.notes() : "";
                annotations = annotations.withNotes(existingNotes + EXECUTE_AFTER_HEADER + ":" + serialized);
                // Re-read bytes after header removal
                requestBytes = request.toByteArray().getBytes();
            }

            // Fast path: skip all variable logic unless the request contains a plausible
            // variable prefix. All Stepper tokens start with "$VAR:", "$DVAR:", or "$GVAR:",
            // so we look for '$' followed by 'V', 'D', or 'G'. This is far more selective
            // than checking for '$' alone (which appears in JS, cookies, etc.).
            boolean mayHaveVariables = false;
            for (int i = 0; i < requestBytes.length - 1; i++) {
                if (requestBytes[i] == '$') {
                    byte next = requestBytes[i + 1];
                    if (next == 'V' || next == 'D' || next == 'G') {
                        mayHaveVariables = true;
                        break;
                    }
                }
            }

            if (mayHaveVariables) {
                // Convert to String only when we know there are potential variables
                String requestString = new String(requestBytes);
                Set<StepSequence> autoExecSequences = sequenceManager.getSequencesToAutoExecute(requestString);
                if (!autoExecSequences.isEmpty()) {
                    int validateEveryN = 1;
                    try {
                        Object val = preferences.getSetting(Globals.PREF_SESSION_VALIDATE_EVERY_N);
                        if (val instanceof Integer i) validateEveryN = Math.max(1, i);
                    } catch (Exception ignored) {}

                    int count = requestCounter.incrementAndGet();
                    boolean shouldValidate = (count % validateEveryN == 0);

                    for (StepSequence seq : autoExecSequences) {
                        if (!alreadyExecuted.contains(seq) && !seq.isExecuting()
                                && !isSequenceOnStack(seq.getTitle())
                                && getStackDepth() < Globals.MAX_SEQUENCE_DEPTH
                                && shouldValidate) {
                            seq.executeBlocking();
                        }
                    }
                }

                if (hasStepVariable(requestString)) {
                    HashMap<StepSequence, List<StepVariable>> allVariables = sequenceManager.getRollingVariablesFromAllSequences();
                    if(allVariables.size() > 0) {
                        if(isUnprocessable(requestBytes) && Boolean.TRUE.equals(preferences.getSetting(Globals.PREF_ENABLE_UNPROCESSABLE_WARNING))){
                            final boolean[] proceed = {true};
                            try {
                                SwingUtilities.invokeAndWait(() -> {
                                    int result = JOptionPane.showConfirmDialog(Stepper.getUI().getUiComponent(),
                                            "The request contains non UTF characters.\nStepper is able to make the replacements, " +
                                                    "but some of the binary data may be lost. Continue?",
                                            "Stepper Replacement Error", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                                    if(result == JOptionPane.NO_OPTION) proceed[0] = false;
                                });
                            } catch (Exception ignored) {}
                            if(!proceed[0])
                                return RequestToBeSentAction.continueWith(request, annotations);
                        }

                        try {
                            requestBytes = makeReplacementsForAllSequences(requestBytes, allVariables);
                            if(preferences.getSetting(Globals.PREF_UPDATE_REQUEST_LENGTH)){
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
                        if(preferences.getSetting(Globals.PREF_UPDATE_REQUEST_LENGTH)){
                            requestBytes = updateContentLength(requestBytes);
                        }
                    } catch (UnsupportedOperationException e) { /* Read-only message */ }
                }

                if (hasGvarVariable(requestString)) {
                    try {
                        requestBytes = makeGvarReplacements(requestBytes, dynamicGlobalVariableManager);
                        if(preferences.getSetting(Globals.PREF_UPDATE_REQUEST_LENGTH)){
                            requestBytes = updateContentLength(requestBytes);
                        }
                    } catch (UnsupportedOperationException e) { /* Read-only message */ }
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
        if (dynamicGlobalVariableManager != null) {
            try {
                String responseText = responseReceived.toString();
                String host = responseReceived.initiatingRequest() != null &&
                              responseReceived.initiatingRequest().httpService() != null ?
                              responseReceived.initiatingRequest().httpService().host() : null;
                dynamicGlobalVariableManager.processResponse(responseText, host);
            } catch (Exception e) {
            }
        }

        if (!isInternalRequest() && sequenceManager != null) {
            try {
                String responseText = null; // Lazy — only convert if there are published vars
                for (StepSequence seq : sequenceManager.getSequences()) {
                    if (seq.isDisabled()) continue;
                    boolean synced = false;
                    for (Step step : seq.getSteps()) {
                        for (StepVariable var : step.getVariableManager().getVariables()) {
                            if (var.isPublished() && var instanceof RegexVariable regexVar && regexVar.isValid()) {
                                if (responseText == null) {
                                    responseText = new String(responseReceived.toByteArray().getBytes());
                                }
                                String oldVal = var.getValue();
                                regexVar.updateFromResponse(responseText);
                                String newVal = var.getValue();
                                if (newVal != null && !newVal.isEmpty() && !newVal.equals(oldVal)) {
                                    synced = true;
                                    Stepper.montoya.logging().logToOutput(
                                        "Stepper-NG passthrough: " + var.getIdentifier()
                                        + " updated: " + (oldVal == null || oldVal.isEmpty() ? "(empty)" : oldVal.substring(0, Math.min(oldVal.length(), 30)))
                                        + " → " + newVal.substring(0, Math.min(newVal.length(), 30)));
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
            replacements.add(new ReplacingInputStream.Replacement(match.getBytes(StandardCharsets.UTF_8), replace.getBytes(StandardCharsets.UTF_8)));
        }
        if (replacements.isEmpty()) return originalContent;
        return applyReplacements(originalContent, replacements);
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
                replacements.add(new ReplacingInputStream.Replacement(match.getBytes(StandardCharsets.UTF_8), replace.getBytes(StandardCharsets.UTF_8)));
            }
        }
        if (replacements.isEmpty()) return originalContent;
        return applyReplacements(originalContent, replacements);
    }

    private static byte[] applyReplacements(byte[] content, List<ReplacingInputStream.Replacement> replacements) {
        ReplacingInputStream inputStream = new ReplacingInputStream(new ByteArrayInputStream(content), replacements);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(content.length);
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = inputStream.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
        } catch (IOException e) {
            Stepper.montoya.logging().logToError("Stepper-NG: Stream replacement error: " + e.getMessage());
        }

        return bos.toByteArray();
    }

    public static byte[] updateContentLength(byte[] request){
        String requestStr = new String(request, StandardCharsets.UTF_8);
        int headerEnd = requestStr.indexOf("\r\n\r\n");
        if (headerEnd == -1) return request; // No body separator found

        String headerPart = requestStr.substring(0, headerEnd);
        String bodyPart = requestStr.substring(headerEnd + 4);
        int bodyLength = bodyPart.getBytes(StandardCharsets.UTF_8).length;

        String[] headerLines = headerPart.split("\r\n");
        StringBuilder newHeaders = new StringBuilder();
        boolean found = false;
        for (String line : headerLines) {
            if (line.toLowerCase().startsWith("content-length:")) {
                found = true;
                continue; // skip old content-length
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

            String sequenceName = nameMatcher.group(1).trim();
            Map<String, String> arguments = extractVariablesFromInfoString(headerValue);

            Optional<StepSequence> execSequence = sequenceManager.getSequences().stream()
                    .filter(sequence -> !sequence.isDisabled() && sequence.getTitle().equalsIgnoreCase(sequenceName))
                    .findFirst();

            if (execSequence.isPresent())
                execSequences.add(new RequestSequenceInformation(execSequence.get(), arguments));
            else
                Stepper.montoya.logging().logToError("Stepper-NG: Could not find execution sequence named: \"" + sequenceName + "\".");
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
                com.google.gson.Gson gson = new com.google.gson.Gson();
                java.lang.reflect.Type mapType = new com.google.gson.reflect.TypeToken<HashMap<String, HashMap<String, String>>>(){}.getType();
                HashMap<String, HashMap<String, String>> allSequences = gson.fromJson(serialized, mapType);
                if (allSequences != null) {
                    for (Map.Entry<String, HashMap<String, String>> entry : allSequences.entrySet()) {
                        String sequenceName = entry.getKey();
                        Optional<StepSequence> execSequence = sequenceManager.getSequences().stream()
                                .filter(sequence -> !sequence.isDisabled() && sequence.getTitle().equalsIgnoreCase(sequenceName))
                                .findFirst();
                        Map<String, String> variables = entry.getValue() != null ? entry.getValue() : new HashMap<>();
                        if (execSequence.isPresent())
                            execSequences.add(new RequestSequenceInformation(execSequence.get(), variables));
                        else
                            Stepper.montoya.logging().logToError("Stepper-NG: Could not find execution sequence named: \"" + sequenceName + "\".");
                    }
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                // Fallback: try the old delimiter format for backward compatibility
                String[] parts = serialized.split(EXECUTE_AFTER_COMMENT_DELIMITER);
                for (String sequenceName : parts) {
                    if (sequenceName == null || sequenceName.trim().isEmpty()) continue;
                    Optional<StepSequence> execSequence = sequenceManager.getSequences().stream()
                            .filter(sequence -> !sequence.isDisabled() && sequence.getTitle().equalsIgnoreCase(sequenceName.trim()))
                            .findFirst();
                    if (execSequence.isPresent())
                        execSequences.add(new RequestSequenceInformation(execSequence.get(), new HashMap<>()));
                }
            }
        }
        return execSequences;
    }

    public static boolean hasHeaderMatchingPattern(List<String> headers, Pattern pattern){
        return headers.stream().anyMatch(s -> pattern.asPredicate().test(s));
    }

    public static byte[] addHeaderToRequest(byte[] request, String header){
        String requestStr = new String(request, StandardCharsets.UTF_8);
        int headerEnd = requestStr.indexOf("\r\n\r\n");
        if (headerEnd == -1) {
            return (requestStr.trim() + "\r\n" + header + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        }
        String headerPart = requestStr.substring(0, headerEnd);
        String bodyPart = requestStr.substring(headerEnd + 4);
        return (headerPart + "\r\n" + header + "\r\n\r\n" + bodyPart).getBytes(StandardCharsets.UTF_8);
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
                replacements.add(new ReplacingInputStream.Replacement(
                        match.getBytes(StandardCharsets.UTF_8),
                        dvar.getValue().getBytes(StandardCharsets.UTF_8)));
            }
        }
        if (replacements.isEmpty()) return originalContent;
        return applyReplacements(originalContent, replacements);
    }

    public static byte[] makeGvarReplacements(byte[] originalContent, DynamicGlobalVariableManager manager) {
        List<ReplacingInputStream.Replacement> replacements = new ArrayList<>();
        for (StaticGlobalVariable svar : manager.getStaticVariables()) {
            if (svar.getValue() != null) {
                String match = StaticGlobalVariable.createGvarString(svar.getIdentifier());
                replacements.add(new ReplacingInputStream.Replacement(
                        match.getBytes(StandardCharsets.UTF_8),
                        svar.getValue().getBytes(StandardCharsets.UTF_8)));
            }
        }
        if (replacements.isEmpty()) return originalContent;
        return applyReplacements(originalContent, replacements);
    }
}
