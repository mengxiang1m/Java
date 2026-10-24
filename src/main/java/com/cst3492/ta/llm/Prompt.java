package com.cst3492.ta.llm;

import java.util.Objects;

/**
 * A rendered prompt: the system instructions plus the user message.
 */
public final class Prompt {

    private final String system;
    private final String user;

    public Prompt(String system, String user) {
        this.system = Objects.requireNonNull(system, "system");
        this.user = Objects.requireNonNull(user, "user");
    }

    public String getSystem() {
        return system;
    }

    public String getUser() {
        return user;
    }
}
