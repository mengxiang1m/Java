package com.cst3492.ta.llm;

/**
 * Supported LLM providers.
 *
 * <p>DeepSeek and the Tongji AI Services Platform both expose an
 * OpenAI-compatible {@code POST /chat/completions} API, so they share a single
 * client; only the base URL and model name differ.</p>
 */
public enum LlmProvider {

    DEEPSEEK("https://api.deepseek.com/v1", "deepseek-chat"),

    /**
     * The Tongji AI Services Platform (https://agi.tongji.edu.cn). The exact
     * OpenAI-compatible base URL and model name depend on the platform's
     * current documentation and the account's available models; they are
     * therefore left as overridable defaults.
     */
    TONGJI("https://agi.tongji.edu.cn/v1", ""),

    /** Any other OpenAI-compatible endpoint; both values must be supplied. */
    CUSTOM("", "");

    private final String defaultBaseUrl;
    private final String defaultModel;

    LlmProvider(String defaultBaseUrl, String defaultModel) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }
}
