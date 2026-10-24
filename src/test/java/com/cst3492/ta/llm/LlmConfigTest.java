package com.cst3492.ta.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmConfigTest {

    @Test
    void deepseekUsesKnownDefaults() {
        LlmConfig config = LlmConfig.deepseek("k");

        assertEquals(LlmProvider.DEEPSEEK, config.getProvider());
        assertEquals("https://api.deepseek.com/v1", config.getBaseUrl());
        assertEquals("deepseek-chat", config.getModel());
    }

    @Test
    void builderOverridesDefaultsForTongji() {
        LlmConfig config = LlmConfig.builder(LlmProvider.TONGJI, "k")
                .baseUrl("https://agi.tongji.edu.cn/v1")
                .model("some-model")
                .temperature(0.0)
                .maxTokens(512)
                .build();

        assertEquals(LlmProvider.TONGJI, config.getProvider());
        assertEquals("some-model", config.getModel());
        assertEquals(0.0, config.getTemperature());
        assertEquals(512, config.getMaxTokens());
    }
}
