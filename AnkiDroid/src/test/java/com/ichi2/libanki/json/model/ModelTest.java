package com.ichi2.libanki.json.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ichi2.anki.AnkiSerialization;

import org.json.JSONException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static com.ichi2.utils.ListUtil.assertListEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModelTest {


    @Test
    public void testSerialize() throws IOException {
        File file = new File("./src/test/assets/one_model.json");


        Model model = AnkiSerialization.getObjectMapper().readValue(file, Model.class);

        assertEquals(Long.valueOf(1500296807433L), model.getId());
        assertEquals("Instruction", model.getName());
        assertEquals("Where", model.getTemplates().get(8).getName());
        assertEquals(Long.valueOf(8L), model.getTemplates().get(8).getOrdinal());


        assertEquals(ModelType.STANDARD, model.getType());
        assertEquals(12, model.getRequirements().get(12).getTemplateOrdinal());
        assertEquals(RequirementType.ALL, model.getRequirements().get(12).getType());
        assertListEquals(Arrays.asList(16L, 17L), model.getRequirements().get(12).getFieldsOrdinal());

        assertEquals(3, model.getAdditionalProperties().get("number of columns"));
    }


    @Test
    public void testDeserialization() throws JsonProcessingException, JSONException {
        Model model = new Model();

        model.setCss("This is css");
        model.setDeckId(55L);
        model.setRequirements(Collections.singletonList(new Requirement(0, RequirementType.ALL, Arrays.asList(1L, 34L))));

        String deserializedJson = AnkiSerialization.getObjectMapper().writeValueAsString(model);

        JsonNode node = AnkiSerialization.getObjectMapper().readTree(deserializedJson);

        assertEquals("This is css", node.get("css").textValue());
        assertEquals(55, node.get("did").longValue());

        ArrayNode reqNode = (ArrayNode) node.get("req").get(0);
        assertEquals(0, reqNode.get(0).intValue());
        assertEquals("all", reqNode.get(1).textValue());
        assertEquals(34, reqNode.get(2).get(1).intValue());
    }


    @Test
    public void testWillKeepUnknownProperties() throws JsonProcessingException, JSONException {
        String json = "{\"id\":151, \"anki\":\"droid\", \"req\": [[0, \"thisIsUnknown\", [2,3]]]}";

        Model model = AnkiSerialization.getObjectMapper().readValue(json, Model.class);

        assertEquals(Long.valueOf(151), model.getId());
        assertEquals("droid", model.getAdditionalProperties().get("anki"));
        assertTrue(model.getRequirements().get(0).getType().isUnknown());
        assertEquals("thisIsUnknown", model.getRequirements().get(0).getType().getValue());


        model.setAdditionalProperty("unknown", new int[] {1, 2});

        String deserializedJson = AnkiSerialization.getObjectMapper().writeValueAsString(model);

        JsonNode node = AnkiSerialization.getObjectMapper().readTree(deserializedJson);

        assertEquals("droid", node.get("anki").textValue());
        assertEquals(2, node.get("unknown").get(1).intValue());

        assertEquals("thisIsUnknown", node.get("req").get(0).get(1).textValue());
    }
}