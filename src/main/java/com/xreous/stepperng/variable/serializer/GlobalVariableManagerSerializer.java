package com.xreous.stepperng.variable.serializer;

import com.xreous.stepperng.sequence.GlobalVariableManager;
import com.xreous.stepperng.variable.StepVariable;
import com.xreous.stepperng.variable.VariableManager;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Vector;

public class GlobalVariableManagerSerializer implements JsonSerializer<GlobalVariableManager>, JsonDeserializer<GlobalVariableManager> {

    @Override
    public GlobalVariableManager deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return null;
    }

    @Override
    public JsonElement serialize(GlobalVariableManager src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonObject();
    }
}
