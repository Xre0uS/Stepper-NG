package com.xreous.stepperng.sequence.serializer;

import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.variable.StepVariable;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class StepSequenceSerializer implements JsonSerializer<StepSequence>, JsonDeserializer<StepSequence> {

    @Override
    public StepSequence deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        String title = obj.get("title") != null ? obj.get("title").getAsString() : "Untitled Sequence";
        StepSequence stepSequence = new StepSequence(title);
        try {
            if (obj.has("sequenceId") && !obj.get("sequenceId").isJsonNull()) {
                stepSequence.setSequenceId(obj.get("sequenceId").getAsString());
            }
            if (obj.has("disabled")) {
                stepSequence.setDisabled(obj.get("disabled").getAsBoolean());
            }
            if (obj.has("maxConsecutiveFailures")) {
                stepSequence.setMaxConsecutiveFailures(obj.get("maxConsecutiveFailures").getAsInt());
            }
            if (obj.has("globals") && obj.getAsJsonObject("globals") != null) {
                List<StepVariable> globalVars = context.deserialize(obj.getAsJsonObject("globals").getAsJsonArray("variables"), new TypeToken<List<StepVariable>>() {}.getType());
                if (globalVars != null) {
                    for (StepVariable variable : globalVars) {
                        stepSequence.getGlobalVariableManager().addVariable(variable);
                    }
                }
            }
            if (obj.has("steps") && obj.getAsJsonArray("steps") != null) {
                ArrayList<Step> steps = context.deserialize(obj.getAsJsonArray("steps"), new TypeToken<ArrayList<Step>>() {}.getType());
                if (steps != null) {
                    for (Step step : steps) {
                        stepSequence.addStep(step);
                    }
                }
            }
            // New format: step IDs
            if (obj.has("validationStepId") && !obj.get("validationStepId").isJsonNull()) {
                stepSequence.setValidationStepId(obj.get("validationStepId").getAsString());
            }
            if (obj.has("postValidationStepId") && !obj.get("postValidationStepId").isJsonNull()) {
                stepSequence.setPostValidationStepId(obj.get("postValidationStepId").getAsString());
            }
            // Backward compat: old index-based format
            if (stepSequence.getValidationStepId() == null
                    && obj.has("validationStepIndex") && !obj.get("validationStepIndex").isJsonNull()) {
                int idx = obj.get("validationStepIndex").getAsInt();
                if (idx >= 0 && idx < stepSequence.getSteps().size()) {
                    stepSequence.setValidationStepId(stepSequence.getSteps().get(idx).getStepId());
                }
            }
            if (stepSequence.getPostValidationStepId() == null
                    && obj.has("postValidationStepIndex") && !obj.get("postValidationStepIndex").isJsonNull()) {
                int idx = obj.get("postValidationStepIndex").getAsInt();
                if (idx >= 0 && idx < stepSequence.getSteps().size()) {
                    stepSequence.setPostValidationStepId(stepSequence.getSteps().get(idx).getStepId());
                }
            }
        } catch (Exception e) {
            com.xreous.stepperng.Stepper.montoya.logging().logToError("Stepper-NG: Error deserializing sequence '" + title + "': " + e.getMessage());
        }
        return stepSequence;
    }

    @Override
    public JsonElement serialize(StepSequence src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("sequenceId", src.getSequenceId());
        json.addProperty("title", src.getTitle());
        json.addProperty("disabled", src.isDisabled());
        if (src.getValidationStepId() != null) {
            json.addProperty("validationStepId", src.getValidationStepId());
        }
        if (src.getPostValidationStepId() != null) {
            json.addProperty("postValidationStepId", src.getPostValidationStepId());
        }
        json.addProperty("maxConsecutiveFailures", src.getMaxConsecutiveFailures());
        json.add("steps", context.serialize(src.getSteps(), new TypeToken<ArrayList<Step>>(){}.getType()));
        return json;
    }
}
