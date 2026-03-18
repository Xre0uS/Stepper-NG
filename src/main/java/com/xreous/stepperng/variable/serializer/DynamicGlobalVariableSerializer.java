package com.xreous.stepperng.variable.serializer;

import com.google.gson.*;
import com.xreous.stepperng.variable.DynamicGlobalVariable;

import java.lang.reflect.Type;

public class DynamicGlobalVariableSerializer implements JsonSerializer<DynamicGlobalVariable>, JsonDeserializer<DynamicGlobalVariable> {
    @Override
    public DynamicGlobalVariable deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        String identifier = obj.has("identifier") ? obj.get("identifier").getAsString() : "";
        String regex = obj.has("regex") ? obj.get("regex").getAsString() : "";
        String hostFilter = obj.has("hostFilter") ? obj.get("hostFilter").getAsString() : null;
        boolean captureFromRequests = obj.has("captureFromRequests") && obj.get("captureFromRequests").getAsBoolean();
        DynamicGlobalVariable var = new DynamicGlobalVariable(identifier, regex, hostFilter, captureFromRequests);
        if (obj.has("value") && !obj.get("value").isJsonNull()) {
            var.setValue(obj.get("value").getAsString());
        }
        return var;
    }

    @Override
    public JsonElement serialize(DynamicGlobalVariable src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("identifier", src.getIdentifier());
        obj.addProperty("regex", src.getRegexString());
        if (src.getHostFilter() != null) {
            obj.addProperty("hostFilter", src.getHostFilter());
        }
        if (src.isCaptureFromRequests()) {
            obj.addProperty("captureFromRequests", true);
        }
        return obj;
    }
}

