package com.cst3492.ta.llm;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for one LLM endpoint.
 *
 * <p>The base URL and model default to the provider's values and can be
 * overridden via the builder (needed for the Tongji platform, whose model name
 * is account-specific). JSON object output is enabled by default for DeepSeek
 * and Tongji, and can be disabled for endpoints without that capability.</p>
 */
public final class LlmConfig {

    private final LlmProvider provider;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final double temperature;
    private final int maxTokens;
    private final Duration timeout;
    private final boolean jsonMode;

    private LlmConfig(Builder builder) {
        this.provider = Objects.requireNonNull(builder.provider, "provider");
        this.baseUrl = builder.baseUrl != null ? builder.baseUrl : provider.defaultBaseUrl();
        this.model = builder.model != null ? builder.model : provider.defaultModel();
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey");
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.timeout = builder.timeout;
        this.jsonMode = builder.jsonMode;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isJsonMode() {
        return jsonMode;
    }

    public static Builder builder(LlmProvider provider, String apiKey) {
        return new Builder(provider, apiKey);
    }

    /** Convenience for the common DeepSeek case. */
    public static LlmConfig deepseek(String apiKey) {
        return builder(LlmProvider.DEEPSEEK, apiKey).build();
    }

    public static final class Builder {

        private final LlmProvider provider;
        private final String apiKey;
        private String baseUrl;
        private String model;
        private double temperature = 0.3;
        private int maxTokens = 2048;
        private Duration timeout = Duration.ofSeconds(60);
        private boolean jsonMode;

        private Builder(LlmProvider provider, String apiKey) {
            this.provider = Objects.requireNonNull(provider, "provider");
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            this.jsonMode = provider != LlmProvider.CUSTOM;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder jsonMode(boolean jsonMode) {
            this.jsonMode = jsonMode;
            return this;
        }

        public LlmConfig build() {
            return new LlmConfig(this);
        }
    }
}
