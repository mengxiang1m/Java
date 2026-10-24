package com.cst3492.ta.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * OpenAI-compatible chat-completions client.
 *
 * <p>Works with any endpoint that implements {@code POST /chat/completions}:
 * DeepSeek, the Tongji AI Services Platform, and most self-hosted gateways.
 * Only the JDK HTTP client and Gson (bundled with the IntelliJ platform) are
 * used, so there is no third-party HTTP/JSON dependency to conflict with the
 * IDE at runtime.</p>
 */
public final class OpenAiCompatibleClient implements LlmClient {

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;

    public OpenAiCompatibleClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    /** Package-visible for tests and for custom TLS/timeout setups. */
    OpenAiCompatibleClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public String complete(LlmConfig config, Prompt prompt) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(prompt, "prompt");

        validateConfig(config);

        String endpoint = config.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(config.getTimeout())
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(config, prompt)))
                    .build();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new LlmException("Invalid LLM configuration: " + e.getMessage(), e);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new LlmException("Failed to reach LLM endpoint: " + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LlmException("LLM endpoint returned HTTP " + response.statusCode()
                    + ": " + truncate(response.body()));
        }
        return extractContent(response.body());
    }

    /**
     * Rejects a configuration that is not ready to send: a blank API key, base
     * URL or model name, or a base URL that is not an absolute http/https URI.
     * Runs before any network call so missing configuration never reaches the
     * wire. The API key value is never included in any error message.
     */
    private static void validateConfig(LlmConfig config) {
        if (isBlank(config.getApiKey())) {
            throw new LlmException("LLM configuration is missing the API key.");
        }
        if (isBlank(config.getBaseUrl())) {
            throw new LlmException("LLM configuration is missing the base URL.");
        }
        if (isBlank(config.getModel())) {
            throw new LlmException("LLM configuration is missing the model name.");
        }
        URI uri;
        try {
            uri = URI.create(config.getBaseUrl());
        } catch (IllegalArgumentException e) {
            throw new LlmException("LLM configuration has an invalid base URL.", e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new LlmException("LLM configuration has an invalid base URL (must be http or https).");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Builds the OpenAI chat-completions request body. Visible for testing. */
    static String buildRequestBody(LlmConfig config, Prompt prompt) {
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", prompt.getSystem());

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", prompt.getUser());

        JsonArray messages = new JsonArray();
        messages.add(system);
        messages.add(user);

        JsonObject root = new JsonObject();
        root.addProperty("model", config.getModel());
        root.add("messages", messages);
        root.addProperty("temperature", config.getTemperature());
        root.addProperty("max_tokens", config.getMaxTokens());
        root.addProperty("stream", false);

        return GSON.toJson(root);
    }

    /** Extracts {@code choices[0].message.content} from a chat-completions response. */
    static String extractContent(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new LlmException("LLM response contains no choices: " + truncate(responseBody));
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || message.get("content") == null || message.get("content").isJsonNull()) {
                throw new LlmException("LLM response has no message content: " + truncate(responseBody));
            }
            return message.get("content").getAsString();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Could not parse LLM response: " + e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
