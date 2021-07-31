package com.ichi2.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import net.bytebuddy.implementation.bytecode.Throw;

import org.junit.Test;

import static org.junit.Assert.*;
import androidx.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class JSONUtilsTest {

    /**
     * Fail if e cause is not expected
     */
    public static void assertJSONExceptionEncapsulate(Class<? extends Throwable> expected, JSONException e) {
        Throwable cause = e.getCause();
        if (expected != null) {
            assertNotNull("\"" + e + "\" has no known cause, expected \"" + expected  + "\".", cause);
            assertEquals(expected, cause.getClass());
        } else {
            assertNull(cause);
        }
    }


    /**
     * @param r fail if r don't throw a JSONException whose cause is org.json.JSONException
     */
    public static void assertThrowsJSONExceptionEncapsulating(Runnable r) {
        assertThrowsJSONExceptionEncapsulating(r, null);
    }

    /**
     * @param r fail if r don't throw a JSONException whose cause is expected
     */
    public static void assertThrowsJSONExceptionEncapsulating(Class<? extends Throwable> expected, Runnable r) {
        assertThrowsJSONExceptionEncapsulating(expected, r, null);
    }

    /**
     * @param r fail with reason if r don't throw a JSONException whose cause is org.json.JSONException
     */
    public static void assertThrowsJSONExceptionEncapsulating(Runnable r, @Nullable String reason) {
        assertThrowsJSONExceptionEncapsulating(null, r, reason);
    }

    /**
     * @param r fail with reason if r don't throw a JSONException whose cause is expected
     */
    public static void assertThrowsJSONExceptionEncapsulating(Class<? extends Throwable> expected, Runnable r, @Nullable String reason) {
        try {
            r.run();
            if (reason == null) {
                reason = "Expected a JSONException. Instead no throw at all.";
            }
            fail(reason);
        } catch (JSONException e) {
            assertJSONExceptionEncapsulate(expected, e);
        }
    }

    @Test
    public void objectToJsonNode() {
        ContainerNode containerNode = new ObjectMapper().createObjectNode();


        assertTrue(JSONUtils.objectToJsonNode("hello").isTextual());
        CharSequence sequence = "seq";
        assertTrue(JSONUtils.objectToJsonNode(sequence).isTextual());
        assertTrue(JSONUtils.objectToJsonNode(11D).isDouble());
        assertTrue(JSONUtils.objectToJsonNode(11F).isFloat());
        assertTrue(JSONUtils.objectToJsonNode(11).isInt());
        assertTrue(JSONUtils.objectToJsonNode(11L).isLong());
        assertTrue(JSONUtils.objectToJsonNode(true).isBoolean());

        JsonNode node = TextNode.valueOf("TextNode");
        assertEquals(node, JSONUtils.objectToJsonNode(node));



        ArrayNode arrayNode = containerNode.arrayNode()
                .add(0)
                .add("a")
                .add(false)
                .add(23.4);
        assertTrue(JSONUtils.objectToJsonNode(arrayNode).isArray());


        ObjectNode objectNode = containerNode.objectNode()
                .put("a", 0)
                .put("b", "a")
                .put("c", false)
                .put("d", 23.4);
        assertTrue(JSONUtils.objectToJsonNode(objectNode).isObject());
    }


    @Test
    public void jsonNodeToObject() {
        ContainerNode containerNode = new ObjectMapper().createObjectNode();

        ArrayNode arrayNode = containerNode.arrayNode()
                .add(0)
                .add("a")
                .add(false)
                .add(23.4);
        assertEquals(new JSONArray(arrayNode), JSONUtils.jsonNodeToObject(arrayNode));

        ObjectNode objectNode = containerNode.objectNode()
                .put("a", 0)
                .put("b", "a")
                .put("c", false)
                .put("d", 23.4);
        assertEquals(new JSONObject(objectNode), JSONUtils.jsonNodeToObject(objectNode));

        assertEquals(JSONObject.NULL, JSONUtils.jsonNodeToObject(containerNode.nullNode()));

        assertEquals(true, JSONUtils.jsonNodeToObject(containerNode.booleanNode(true)));

        assertEquals("qwERQ", JSONUtils.jsonNodeToObject(containerNode.textNode("qwERQ")));

        assertEquals(11, JSONUtils.jsonNodeToObject(containerNode.numberNode(11)));
        assertEquals(11L, JSONUtils.jsonNodeToObject(containerNode.numberNode(11L)));
        assertEquals(12D, JSONUtils.jsonNodeToObject(containerNode.numberNode(12D)));
        assertEquals(13F, JSONUtils.jsonNodeToObject(containerNode.numberNode(13F)));
    }
}