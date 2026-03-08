package com.xreous.stepperng.step.serializer;

import com.xreous.stepperng.condition.ConditionFailAction;
import com.xreous.stepperng.condition.StepCondition;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.step.Step;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class StepSerializer implements JsonSerializer<Step>, JsonDeserializer<Step> {

    @Override
    public Step deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        Step step = new Step();
        try {
            step.setTitle(jsonObject.has("title") ? jsonObject.get("title").getAsString() : "Unnamed Step");
            step.setHostname(jsonObject.has("host") && !jsonObject.get("host").isJsonNull() ? jsonObject.get("host").getAsString() : "");
            step.setPort(jsonObject.has("port") && !jsonObject.get("port").isJsonNull() ? jsonObject.get("port").getAsInt() : 443);
            step.setSSL(!jsonObject.has("ssl") || jsonObject.get("ssl").isJsonNull() || jsonObject.get("ssl").getAsBoolean());
            step.setEnabled(!jsonObject.has("enabled") || jsonObject.get("enabled").getAsBoolean());
            step.setRequestBody(jsonObject.has("request") && !jsonObject.get("request").isJsonNull() ? jsonObject.get("request").getAsString().getBytes() : "".getBytes());
            if (jsonObject.has("condition") && jsonObject.get("condition").isJsonObject()) {
                JsonObject condObj = jsonObject.getAsJsonObject("condition");
                StepCondition cond = new StepCondition();
                if (condObj.has("type")) cond.setType(StepCondition.ConditionType.valueOf(condObj.get("type").getAsString()));
                if (condObj.has("pattern")) cond.setPattern(condObj.get("pattern").getAsString());
                if (condObj.has("matchMode")) {
                    String modeStr = condObj.get("matchMode").getAsString();
                    // backward compat: IF_MATCH → MATCHES, IF_NOT_MATCH → DOES_NOT_MATCH
                    if ("IF_MATCH".equals(modeStr)) modeStr = "MATCHES";
                    else if ("IF_NOT_MATCH".equals(modeStr)) modeStr = "DOES_NOT_MATCH";
                    cond.setMatchMode(StepCondition.MatchMode.valueOf(modeStr));
                } else if (condObj.has("negate")) {
                    cond.setMatchMode(condObj.get("negate").getAsBoolean()
                            ? StepCondition.MatchMode.DOES_NOT_MATCH : StepCondition.MatchMode.MATCHES);
                }
                if (condObj.has("failAction")) cond.setAction(parseAction(condObj.get("failAction").getAsString()));
                if (condObj.has("action")) cond.setAction(parseAction(condObj.get("action").getAsString()));
                if (condObj.has("failGotoTarget")) cond.setGotoTarget(condObj.get("failGotoTarget").getAsString());
                if (condObj.has("gotoTarget")) cond.setGotoTarget(condObj.get("gotoTarget").getAsString());
                if (condObj.has("retryCount")) cond.setRetryCount(condObj.get("retryCount").getAsInt());
                if (condObj.has("retryDelayMs")) cond.setRetryDelayMs(condObj.get("retryDelayMs").getAsLong());
                if (condObj.has("elseAction")) cond.setElseAction(parseAction(condObj.get("elseAction").getAsString()));
                if (condObj.has("elseGotoTarget")) cond.setElseGotoTarget(condObj.get("elseGotoTarget").getAsString());
                step.setCondition(cond);
            }
            if (jsonObject.has("variables") && jsonObject.getAsJsonArray("variables") != null) {
                List<StepVariable> variables = context.deserialize(
                        jsonObject.getAsJsonArray("variables"), new TypeToken<List<StepVariable>>() {}.getType());
                if (variables != null) {
                    for (StepVariable variable : variables) {
                        step.getVariableManager().addVariable(variable);
                    }
                }
            }
        } catch (Exception e) {
            com.xreous.stepperng.Stepper.montoya.logging().logToError("Stepper-NG: Error deserializing step: " + e.getMessage());
        }
        return step;
    }

    @Override
    public JsonElement serialize(Step src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("title", src.getTitle());
        json.addProperty("host", src.getHostname());
        json.addProperty("port", src.getPort());
        json.addProperty("ssl", src.isSSL());
        json.addProperty("enabled", src.isEnabled());
        if (src.getCondition() != null && src.getCondition().isConfigured()) {
            JsonObject condObj = new JsonObject();
            condObj.addProperty("type", src.getCondition().getType().name());
            condObj.addProperty("pattern", src.getCondition().getPattern());
            condObj.addProperty("matchMode", src.getCondition().getMatchMode().name());
            condObj.addProperty("action", src.getCondition().getAction().name());
            condObj.addProperty("gotoTarget", src.getCondition().getGotoTarget());
            condObj.addProperty("retryCount", src.getCondition().getRetryCount());
            condObj.addProperty("retryDelayMs", src.getCondition().getRetryDelayMs());
            condObj.addProperty("elseAction", src.getCondition().getElseAction().name());
            condObj.addProperty("elseGotoTarget", src.getCondition().getElseGotoTarget());
            json.add("condition", condObj);
        }
        json.addProperty("request", new String(src.getRequest()));
        json.add("variables", context.serialize(src.getVariableManager().getVariables(), new TypeToken<List<StepVariable>>(){}.getType()));
        return json;
    }

    private static ConditionFailAction parseAction(String value) {
        return switch (value) {
            case "SKIP", "STOP" -> ConditionFailAction.SKIP_REMAINING;
            default -> ConditionFailAction.valueOf(value);
        };
    }
}
