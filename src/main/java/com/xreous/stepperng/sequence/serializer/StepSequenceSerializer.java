package com.xreous.stepperng.sequence.serializer;

import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.variable.StepVariable;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Vector;

public class StepSequenceSerializer implements JsonSerializer<StepSequence>, JsonDeserializer<StepSequence> {

    @Override
    public StepSequence deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        String title = obj.get("title") != null ? obj.get("title").getAsString() : "Untitled Sequence";
        StepSequence stepSequence = new StepSequence(title);
        try {
            if (obj.has("disabled")) {
                stepSequence.setDisabled(obj.get("disabled").getAsBoolean());
            }
            if (obj.has("validationStepIndex") && !obj.get("validationStepIndex").isJsonNull()) {
                stepSequence.setValidationStepIndex(obj.get("validationStepIndex").getAsInt());
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
                Vector<Step> steps = context.deserialize(obj.getAsJsonArray("steps"), new TypeToken<Vector<Step>>() {}.getType());
                if (steps != null) {
                    for (Step step : steps) {
                        stepSequence.addStep(step);
                    }
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
        json.addProperty("title", src.getTitle());
        json.addProperty("disabled", src.isDisabled());
        if (src.getValidationStepIndex() != null) {
            json.addProperty("validationStepIndex", src.getValidationStepIndex());
        }
        json.add("steps", context.serialize(src.getSteps(), new TypeToken<Vector<Step>>(){}.getType()));
        return json;
    }
}
