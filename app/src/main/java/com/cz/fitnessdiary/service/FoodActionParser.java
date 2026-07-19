package com.cz.fitnessdiary.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts the structured food action from the different response shapes used by MiMo.
 * The model is asked for JSON, but may still add a code fence, an explanation, an
 * action tag, or return the JSON as an escaped string.
 */
public final class FoodActionParser {
    private static final String ACTION_START = "<action>";
    private static final String ACTION_END = "</action>";

    private FoodActionParser() {
    }

    /** Returns a normalized action object, or null when no supported action is present. */
    public static JsonObject parseAction(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        String value = response.trim();
        addCandidate(candidates, extractActionTag(value));
        addCandidate(candidates, value);
        addCandidate(candidates, stripCodeFence(value));
        addBalancedCandidates(candidates, value);

        for (String candidate : candidates) {
            JsonObject action = parseCandidate(candidate, 0);
            if (action != null) {
                return normalizeAction(action);
            }
        }
        return null;
    }

    /** Returns an optional model-authored reply from a JSON envelope. */
    public static String extractReply(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "";
        }
        List<String> candidates = new ArrayList<>();
        String value = response.trim();
        addCandidate(candidates, extractActionTag(value));
        addCandidate(candidates, value);
        addCandidate(candidates, stripCodeFence(value));
        addBalancedCandidates(candidates, value);
        for (String candidate : candidates) {
            JsonObject envelope = parseObject(candidate, 0);
            if (envelope == null) {
                continue;
            }
            String reply = stringValue(envelope, "reply");
            if (!reply.isEmpty()) {
                return reply;
            }
            JsonObject action = objectValue(envelope, "action");
            reply = stringValue(action, "reply");
            if (!reply.isEmpty()) {
                return reply;
            }
        }
        return "";
    }

    private static JsonObject parseCandidate(String candidate, int depth) {
        JsonObject object = parseObject(candidate, depth);
        if (object == null) {
            return null;
        }
        JsonObject action = objectValue(object, "action");
        if (action == null) {
            action = objectValue(object, "data");
        }
        if (action == null && object.has("type")) {
            action = object;
        }
        if (action == null && hasItems(object)) {
            action = object;
            action.addProperty("type", "FOOD");
        }
        if (action == null) {
            return null;
        }

        String type = stringValue(action, "type");
        if (type.isEmpty() && hasItems(action)) {
            type = "FOOD";
        }
        if (!"FOOD".equalsIgnoreCase(type)
                && !"PLAN".equalsIgnoreCase(type)
                && !"MULTI".equalsIgnoreCase(type)) {
            return null;
        }
        return action;
    }

    private static JsonObject parseObject(String candidate, int depth) {
        if (candidate == null || candidate.trim().isEmpty() || depth > 2) {
            return null;
        }
        String value = candidate.trim();
        try {
            JsonElement parsed = JsonParser.parseString(value);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            if (parsed.isJsonArray()) {
                JsonObject wrapped = new JsonObject();
                wrapped.addProperty("type", "FOOD");
                wrapped.add("items", parsed.getAsJsonArray());
                return wrapped;
            }
            if (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString()) {
                String nested = parsed.getAsString().trim();
                if (!nested.equals(value)) {
                    return parseObject(nested, depth + 1);
                }
            }
        } catch (Exception ignored) {
            // Try the common escaped-object form below.
        }

        if (value.indexOf("\\\"") >= 0 && value.indexOf('{') >= 0) {
            try {
                String unescaped = value.replace("\\\"", "\"")
                        .replace("\\\\n", "\n")
                        .replace("\\\\r", "\r");
                if (!unescaped.equals(value)) {
                    return parseObject(unescaped, depth + 1);
                }
            } catch (Exception ignored) {
                // Not a valid escaped JSON payload.
            }
        }
        return null;
    }

    private static JsonObject normalizeAction(JsonObject action) {
        JsonObject normalized = action.deepCopy();
        String type = stringValue(normalized, "type");
        if (type.isEmpty() && hasItems(normalized)) {
            type = "FOOD";
        }
        if (arrayValue(normalized, "items") == null) {
            JsonArray aliases = arrayValue(normalized, "food_items");
            if (aliases == null) {
                aliases = arrayValue(normalized, "foods");
            }
            if (aliases != null) {
                normalized.add("items", aliases);
            }
        }
        normalized.addProperty("type", type.toUpperCase(java.util.Locale.ROOT));
        return normalized;
    }

    private static boolean hasItems(JsonObject object) {
        return object != null && (arrayValue(object, "items") != null
                || arrayValue(object, "food_items") != null
                || arrayValue(object, "foods") != null);
    }

    private static JsonObject objectValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }

    private static JsonArray arrayValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private static String stringValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String extractActionTag(String value) {
        int start = value.indexOf(ACTION_START);
        if (start < 0) {
            return null;
        }
        int end = value.indexOf(ACTION_END, start + ACTION_START.length());
        return end > start
                ? value.substring(start + ACTION_START.length(), end).trim() : null;
    }

    private static String stripCodeFence(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.trim();
        if (!stripped.startsWith("```")) {
            return stripped;
        }
        stripped = stripped.replaceFirst("^```(?:json)?\\s*", "");
        return stripped.replaceFirst("\\s*```$", "").trim();
    }

    /** Adds complete JSON objects/arrays embedded in natural-language text. */
    private static void addBalancedCandidates(List<String> candidates, String value) {
        if (value == null) {
            return;
        }
        for (int start = 0; start < value.length(); start++) {
            char opening = value.charAt(start);
            if (opening != '{' && opening != '[') {
                continue;
            }
            int depth = 0;
            boolean quoted = false;
            boolean escaped = false;
            char expectedClosing = opening == '{' ? '}' : ']';
            for (int index = start; index < value.length(); index++) {
                char current = value.charAt(index);
                if (quoted) {
                    if (escaped) {
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if (current == '"') {
                        quoted = false;
                    }
                    continue;
                }
                if (current == '"') {
                    quoted = true;
                } else if (current == opening || current == (opening == '{' ? '[' : '{')) {
                    depth++;
                } else if (current == expectedClosing || current == (opening == '{' ? ']' : '}')) {
                    depth--;
                    if (depth == 0) {
                        addCandidate(candidates, value.substring(start, index + 1));
                        break;
                    }
                }
            }
        }
    }

    private static void addCandidate(List<String> candidates, String candidate) {
        if (candidate != null && !candidate.trim().isEmpty() && !candidates.contains(candidate)) {
            candidates.add(candidate.trim());
        }
    }
}
