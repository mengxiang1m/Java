package com.cst3492.assistant.chat;

/** Plain text only: user content is never interpreted as HTML. */
public record ChatMessage(Role role, String text) {
    public enum Role {
        USER("You"), EDITOR_CONTEXT("Editor code context (not executed)"),
        ASSISTANT("Assistant"), ERROR("Error");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
