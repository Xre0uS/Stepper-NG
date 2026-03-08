package com.xreous.stepperng.variable.serializer;

import com.google.gson.*;
import com.xreous.stepperng.variable.StaticGlobalVariable;

import java.lang.reflect.Type;

public class StaticGlobalVariableSerializer implements JsonSerializer<StaticGlobalVariable>, JsonDeserializer<StaticGlobalVariable> {
    @Override
    public StaticGlobalVariable deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        String identifier = obj.has("identifier") ? obj.get("identifier").getAsString() : "";
        String value = obj.has("value") ? obj.get("value").getAsString() : "";
        return new StaticGlobalVariable(identifier, value);
    }

    @Override
    public JsonElement serialize(StaticGlobalVariable src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("identifier", src.getIdentifier());
        obj.addProperty("value", src.getValue());
        return obj;
    }
}

