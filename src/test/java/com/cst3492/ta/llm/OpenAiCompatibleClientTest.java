package com.cst3492.ta.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientTest {

    private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();

    @Test
    void buildsRequestWithModelAndBothRoles() {
        LlmConfig config = LlmConfig.deepseek("secret");
        String body = OpenAiCompatibleClient.buildRequestBody(config, new Prompt("sys", "user"));

        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        assertEquals("deepseek-chat", root.get("model").getAsString());
        assertEquals(false, root.get("stream").getAsBoolean());

        JsonArray messages = root.getAsJsonArray("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
    }

    @Test
    void escapesJsonSpecialCharactersInContent() {
        String tricky = "user says \"hi\"\nwith a newline";
        String body = OpenAiCompatibleClient.buildRequestBody(
                LlmConfig.deepseek("k"), new Prompt("sys", tricky));

        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String roundTripped = root.getAsJsonArray("messages")
                .get(1).getAsJsonObject().get("content").getAsString();

        assertEquals(tricky, roundTripped);
    }

    @Test
    void extractsContentFromOpenAiResponse() {
        String response = "{\"id\":\"1\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}]}";

        assertEquals("Hello", OpenAiCompatibleClient.extractContent(response));
    }

    @Test
    void throwsWhenResponseHasNoChoices() {
        assertThrows(LlmException.class,
                () -> OpenAiCompatibleClient.extractContent("{\"choices\":[]}"));
    }

    @Test
    void throwsWhenApiKeyIsBlank() {
        LlmConfig config = LlmConfig.deepseek("   ");
        LlmException ex = assertThrows(LlmException.class,
                () -> client.complete(config, new Prompt("sys", "user")));
        assertTrue(ex.getMessage().contains("API key"));
    }

    @Test
    void throwsWhenCustomBaseUrlIsMissing() {
        LlmConfig config = LlmConfig.builder(LlmProvider.CUSTOM, "k").model("m").build();
        LlmException ex = assertThrows(LlmException.class,
                () -> client.complete(config, new Prompt("sys", "user")));
        assertTrue(ex.getMessage().contains("base URL"));
    }

    @Test
    void throwsWhenTongjiModelIsMissing() {
        LlmConfig config = LlmConfig.builder(LlmProvider.TONGJI, "k").build();
        LlmException ex = assertThrows(LlmException.class,
                () -> client.complete(config, new Prompt("sys", "user")));
        assertTrue(ex.getMessage().contains("model"));
    }

    @Test
    void throwsWhenBaseUrlIsNotHttp() {
        LlmConfig config = LlmConfig.builder(LlmProvider.CUSTOM, "k")
                .baseUrl("ftp://example.com").model("m").build();
        LlmException ex = assertThrows(LlmException.class,
                () -> client.complete(config, new Prompt("sys", "user")));
        assertTrue(ex.getMessage().contains("base URL"));
    }

    @Test
    void throwsWhenBaseUrlIsMalformed() {
        LlmConfig config = LlmConfig.builder(LlmProvider.CUSTOM, "k")
                .baseUrl("not a url").model("m").build();
        LlmException ex = assertThrows(LlmException.class,
                () -> client.complete(config, new Prompt("sys", "user")));
        assertTrue(ex.getMessage().contains("base URL"));
    }
}
