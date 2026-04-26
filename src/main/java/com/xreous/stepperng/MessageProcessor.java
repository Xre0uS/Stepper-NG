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
    public static final Pattern SEQUENCE_NAME_PATTERN = Pattern.compile("^([^:{]+)(?::?)");
    public static final Pattern RANGE_PATTERN = Pattern.compile("\\{\\s*(\\d+)\\s*(?::\\s*(\\d+)\\s*)?}");
    public static final Pattern VARIABLE_LIST_PATTERN = Pattern.compile("[^:]+(:\\s*(?<variables>.+))?");
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("(?<key>[^=]+)=(?<value>[^;]+);?");
    public static final Pattern SINGLE_VARIABLE_PATTERN = Pattern.compile("(?<key>[^=]+)=(?<value>.*)");

    // Lazy ThreadLocals: avoid pinning state to every Burp worker that only reads the stack.
    private static final ThreadLocal<Boolean> internalRequestFlag = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<String>> executionStack = new ThreadLocal<>();
    private static final AtomicInteger requestCounter = new AtomicInteger(0);

    public static final Pattern CROSS_SEQ_VAR_PATTERN = Pattern.compile("\\$VAR:([^:$]+):([^$]+)\\$");

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    public static void markInternalRequest() {
        internalRequestFlag.set(Boolean.TRUE);
    }

    public static void unmarkInternalRequest() {
        internalRequestFlag.remove();
    }

    public static boolean isInternalRequest() {
        return internalRequestFlag.get() != null;
    }

    public static void pushSequence(String sequenceName) {
        ArrayDeque<String> stack = executionStack.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            executionStack.set(stack);
        }
        stack.push(sequenceName);
    }

    public static void popSequence() {
        ArrayDeque<String> stack = executionStack.get();
        if (stack == null) return;
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) executionStack.remove();
    }

    public static boolean isSequenceOnStack(String sequenceName) {
        ArrayDeque<String> stack = executionStack.get();
        return stack != null && stack.contains(sequenceName);
    }

    public static int getStackDepth() {
        ArrayDeque<String> stack = executionStack.get();
        return stack == null ? 0 : stack.size();
    }

    public static void cleanup() {
        internalRequestFlag.remove();
        executionStack.remove();
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

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        HttpRequest request = requestToBeSent;
        Annotations annotations = requestToBeSent.annotations();

        if (isInternalRequest()) {
            return RequestToBeSentAction.continueWith(request, annotations);
        }

        if(isValidTool(requestToBeSent.toolSource())){
            List<String> headers = request.headers().stream().map(h -> h.name() + ": " + h.value()).toList();

            Map<String, String> standaloneArgs = extractArgumentsFromHeaders(headers, EXECUTE_VAR_HEADER_PATTERN);

            Set<StepSequence> alreadyExecuted = new HashSet<>();

            List<RequestSequenceInformation> preExecSequences = extractExecSequencesFromHeaders(headers, EXECUTE_BEFORE_HEADER_PATTERN);
            if(!preExecSequences.isEmpty()){
                request = removeHeaderMatchingPattern(request, EXECUTE_BEFORE_HEADER_PATTERN);
                request = removeHeaderMatchingPattern(request, EXECUTE_VAR_HEADER_PATTERN);
                for (RequestSequenceInformation info : preExecSequences) {
                    Map<String, String> merged = new HashMap<>(standaloneArgs);
                    merged.putAll(info.arguments);
                    List<Step> snapshot = info.sequence.getSteps();
                    if (info.upToIndexInclusive >= 0 && info.upToIndexInclusive < snapshot.size()) {
                        info.sequence.executeThroughStep(snapshot.get(info.upToIndexInclusive));
                    } else {
                        info.sequence.executeBlocking(merged);
                    }
                    alreadyExecuted.add(info.sequence);
                }
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
            }

            byte[] requestBytes = request.toByteArray().getBytes();
            String requestString = new String(requestBytes, StandardCharsets.UTF_8);
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

            boolean mutated = false;

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
                    boolean shouldValidate = (((count - 1) % validateEveryN) == 0);

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
                    if(!allVariables.isEmpty()) {
                        try {
                            byte[] before = requestBytes;
                            requestBytes = makeReplacementsForAllSequences(requestBytes, allVariables);
                            if (requestBytes != before) mutated = true;
                        } catch (Exception e) {
                            Stepper.montoya.logging().logToError("Stepper-NG: Variable replacement error: " + e.getMessage());
                        }
                    }
                }

                if (hasDvarVariable(requestString)) {
                    try {
                        byte[] before = requestBytes;
                        requestBytes = makeDvarReplacements(requestBytes, dynamicGlobalVariableManager);
                        if (requestBytes != before) mutated = true;
                    } catch (UnsupportedOperationException e) {
                        Stepper.montoya.logging().logToError("Stepper-NG: DVAR replacement error: " + e.getMessage());
                    }
                }

                if (hasGvarVariable(requestString)) {
                    try {
                        byte[] before = requestBytes;
                        requestBytes = makeGvarReplacements(requestBytes, dynamicGlobalVariableManager);
                        if (requestBytes != before) mutated = true;
                    } catch (UnsupportedOperationException e) {
                        Stepper.montoya.logging().logToError("Stepper-NG: GVAR replacement error: " + e.getMessage());
                    }
                }
            }

            if (mutated && getBoolPref(Globals.PREF_UPDATE_REQUEST_LENGTH, true)) {
                requestBytes = updateContentLength(requestBytes);
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
                    responseText = new String(responseReceived.toByteArray().getBytes(), StandardCharsets.UTF_8);
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

    /**
     * Re-computes Content-Length using byte offsets so non-UTF-8 bodies aren't corrupted.
     */
    public static byte[] updateContentLength(byte[] request){
        int headerEnd = indexOf(request, CRLF_CRLF);
        if (headerEnd < 0) return request;

        int bodyStart = headerEnd + 4;
        int bodyLength = request.length - bodyStart;

        String headerPart = new String(request, 0, headerEnd, StandardCharsets.ISO_8859_1);
        String[] headerLines = headerPart.split("\r\n");
        StringBuilder newHeaders = new StringBuilder(headerPart.length() + 32);
        boolean found = false;
        for (String line : headerLines) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, Math.min(line.length(), 15))) {
                found = true;
                continue;
            }
            newHeaders.append(line).append("\r\n");
        }
        if (bodyLength > 0 || found) {
            newHeaders.append("Content-Length: ").append(bodyLength).append("\r\n");
        }
        newHeaders.append("\r\n");

        byte[] headerBytes = newHeaders.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = new byte[headerBytes.length + bodyLength];
        System.arraycopy(headerBytes, 0, out, 0, headerBytes.length);
        System.arraycopy(request, bodyStart, out, headerBytes.length, bodyLength);
        return out;
    }

    private static final byte[] CRLF_CRLF = {'\r', '\n', '\r', '\n'};

    private static int indexOf(byte[] haystack, byte[] needle) {
        int limit = haystack.length - needle.length;
        outer:
        for (int i = 0; i <= limit; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
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

            int upToIndex = -1;
            Matcher rangeMatcher = RANGE_PATTERN.matcher(headerValue);
            if (rangeMatcher.find()) {
                // {N} -> [0..N]; {start:end} -> [0..end]  (start currently unused, always runs from 0)
                String endGroup = rangeMatcher.group(2) != null ? rangeMatcher.group(2) : rangeMatcher.group(1);
                try { upToIndex = Integer.parseInt(endGroup); } catch (NumberFormatException ignored) {}
            }

            Optional<StepSequence> execSequence = sequenceManager.findSequence(sequenceNameOrId);

            if (execSequence.isPresent())
                execSequences.add(new RequestSequenceInformation(execSequence.get(), arguments, upToIndex));
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
        Set<String> names = new LinkedHashSet<>();
        for (burp.api.montoya.http.message.HttpHeader header : request.headers()) {
            String headerLine = header.name() + ": " + header.value();
            if (pattern.matcher(headerLine).matches()) {
                names.add(header.name());
            }
        }
        for (String n : names) request = request.withRemovedHeader(n);
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
