package com.cz.fitnessdiary.service;

import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FoodActionParserTest {
    private static final String FOOD_JSON = "{\"type\":\"FOOD\",\"items\":["
            + "{\"name\":\"白米饭\",\"amount\":1,\"unit\":\"碗\","
            + "\"calories\":230,\"protein\":5,\"carbs\":50,\"fat\":0.5},"
            + "{\"name\":\"炒肉片\",\"amount\":1,\"unit\":\"份\","
            + "\"calories\":260}]}";

    @Test
    public void acceptsJsonInCodeFenceAndActionTag() {
        JsonObject fenced = FoodActionParser.parseAction("```json\n" + FOOD_JSON + "\n```");
        JsonObject tagged = FoodActionParser.parseAction("识别结果：\n<action>" + FOOD_JSON + "</action>");

        assertNotNull(fenced);
        assertNotNull(tagged);
        assertEquals("FOOD", fenced.get("type").getAsString());
        assertEquals(2, tagged.getAsJsonArray("items").size());
    }

    @Test
    public void acceptsEscapedJsonStringAndPlainFoodArray() {
        String escaped = "\"" + FOOD_JSON.replace("\"", "\\\"") + "\"";
        JsonObject escapedAction = FoodActionParser.parseAction(escaped);
        JsonObject arrayAction = FoodActionParser.parseAction(
                "[ {\"name\":\"鸡蛋\",\"amount\":1,\"unit\":\"个\"} ]");

        assertNotNull(escapedAction);
        assertNotNull(arrayAction);
        assertEquals(2, escapedAction.getAsJsonArray("items").size());
        assertEquals("FOOD", arrayAction.get("type").getAsString());
    }

    @Test
    public void readsReplyFromEnvelope() {
        String response = "{\"reply\":\"这餐搭配不错，请确认后记录。\",\"action\":" + FOOD_JSON + "}";

        assertEquals("这餐搭配不错，请确认后记录。", FoodActionParser.extractReply(response));
    }
}
