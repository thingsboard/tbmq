/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.common.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.thingsboard.mqtt.broker.common.data.kv.KvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class JsonConverter {

    private static final Gson GSON = new Gson();
    private static final String CAN_T_PARSE_VALUE = "Can't parse value: ";

    private static void convertToTelemetry(JsonElement jsonElement, long systemTs, Map<Long, List<KvEntry>> result) {
        if (jsonElement.isJsonObject()) {
            parseObject(result, systemTs, jsonElement.getAsJsonObject());
        } else if (jsonElement.isJsonArray()) {
            jsonElement.getAsJsonArray().forEach(je -> {
                if (je.isJsonObject()) {
                    parseObject(result, systemTs, je.getAsJsonObject());
                } else {
                    throw new JsonSyntaxException(CAN_T_PARSE_VALUE + je);
                }
            });
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + jsonElement);
        }
    }

    private static List<KvEntry> parseValues(JsonObject valuesObject) {
        List<KvEntry> result = new ArrayList<>();
        for (Entry<String, JsonElement> valueEntry : valuesObject.entrySet()) {
            JsonElement element = valueEntry.getValue();
            if (element.isJsonPrimitive()) {
                JsonPrimitive value = element.getAsJsonPrimitive();
                if (value.isNumber()) {
                    result.add(new LongDataEntry(valueEntry.getKey(), value.getAsLong()));
                } else {
                    throw new JsonSyntaxException(CAN_T_PARSE_VALUE + value);
                }
            } else {
                throw new JsonSyntaxException(CAN_T_PARSE_VALUE + element);
            }
        }
        return result;
    }

    public static Map<Long, List<KvEntry>> convertToTelemetry(JsonElement jsonElement, long systemTs) throws
            JsonSyntaxException {
        return convertToTelemetry(jsonElement, systemTs, false);
    }

    public static Map<Long, List<KvEntry>> convertToTelemetry(JsonElement jsonElement, long systemTs, boolean sorted) throws
            JsonSyntaxException {
        Map<Long, List<KvEntry>> result = sorted ? new TreeMap<>() : new HashMap<>();
        convertToTelemetry(jsonElement, systemTs, result);
        return result;
    }


    private static void parseObject(Map<Long, List<KvEntry>> result, long systemTs, JsonObject jo) {
        if (jo.has("ts") && jo.has("values")) {
            parseWithTs(result, jo);
        } else {
            parseWithoutTs(result, systemTs, jo);
        }
    }

    private static void parseWithoutTs(Map<Long, List<KvEntry>> result, long systemTs, JsonObject jo) {
        for (KvEntry entry : parseValues(jo)) {
            result.computeIfAbsent(systemTs, tmp -> new ArrayList<>()).add(entry);
        }
    }

    public static void parseWithTs(Map<Long, List<KvEntry>> result, JsonObject jo) {
        long ts = jo.get("ts").getAsLong();
        JsonObject valuesObject = jo.get("values").getAsJsonObject();
        for (KvEntry entry : parseValues(valuesObject)) {
            result.computeIfAbsent(ts, tmp -> new ArrayList<>()).add(entry);
        }
    }

    public static JsonElement parse(String json) {
        return JsonParser.parseString(json);
    }

    public static <T> T parse(String json, Class<T> clazz) {
        return fromJson(parse(json), clazz);
    }

    public static String toJson(JsonElement element) {
        return GSON.toJson(element);
    }

    public static JsonObject toJsonObject(Object o) {
        return (JsonObject) GSON.toJsonTree(o);
    }

    public static <T> T fromJson(JsonElement element, Class<T> type) {
        return GSON.fromJson(element, type);
    }

}
