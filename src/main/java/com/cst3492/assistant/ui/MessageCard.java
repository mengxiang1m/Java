package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatMessage;
import com.cst3492.assistant.chat.ChatReply;
import com.intellij.util.ui.JBUI;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Consumer;

/** A conversation row owns its immutable message/reply; status is visual metadata, not body text. */
final class MessageCard extends JPanel {
    private final ChatMessage message;
    private final ChatReply reply;
    private final SourceCards sources;
    private final MarkdownView body;

    MessageCard(ChatMessage message, ChatReply reply, Runnable retry, Consumer<String> copy) {
        super(new BorderLayout());
        this.message = message;
        this.reply = reply;
        setOpaque(false);
        boolean user = message.role() == ChatMessage.Role.USER;
        setBorder(JBUI.Borders.empty(0, user ? 32 : 0, 14, user ? 0 : 8));
        JPanel surface = ChatUi.card(user ? ChatUi.CARD : ChatUi.SURFACE);
        surface.setLayout(new BorderLayout(0, JBUI.scale(10)));
        add(surface, BorderLayout.CENTER);

        String label = user ? "You" : message.role() == ChatMessage.Role.EDITOR_CONTEXT
                ? "Attached Java code · not executed" : "✦  CST3492 Assistant";
        if (reply != null) {
            label += switch (reply.status()) {
                case MOCK -> "  ·  Mock / test data";
                case NORMAL -> "  ·  Answer";
                case NO_SOURCES -> "  ·  No course sources";
                case ERROR -> "  ·  Request failed";
            };
        }
        WrappingLabel heading = new WrappingLabel(label);
        heading.setFont(ChatUi.font().deriveFont(Font.BOLD));
        heading.setForeground(reply != null && reply.status() == ChatReply.Status.ERROR ? ChatUi.ERROR
                : user ? ChatUi.MUTED : ChatUi.ACCENT);
        surface.add(heading, BorderLayout.NORTH);

        sources = reply != null && !reply.sources().isEmpty() ? new SourceCards(reply.sources(), copy) : null;
        body = new MarkdownView(message.text(), reply != null, sources == null ? null : () -> {
            sources.setExpanded(true);
            sources.scrollRectToVisible(new java.awt.Rectangle(0, 0, sources.getWidth(), 40));
        });
        if (message.role() == ChatMessage.Role.EDITOR_CONTEXT) {
            javax.swing.text.SimpleAttributeSet code = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setFontFamily(code, Font.MONOSPACED);
            body.getStyledDocument().setCharacterAttributes(0, body.getDocument().getLength(), code, false);
        }
        surface.add(body, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        footer.setOpaque(false);
        if (sources != null) { footer.add(sources, BorderLayout.CENTER); }
        if (reply != null && reply.status() == ChatReply.Status.ERROR && retry != null) {
            JButton retryButton = ChatUi.button("Retry question", false);
            retryButton.addActionListener(event -> retry.run());
            footer.add(retryButton, BorderLayout.SOUTH);
        }
        if (footer.getComponentCount() > 0) { surface.add(footer, BorderLayout.SOUTH); }
    }

    ChatMessage message() { return message; }
    ChatReply reply() { return reply; }
    MarkdownView body() { return body; }
    SourceCards sources() { return sources; }

    @Override
    public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    @Override
    public Dimension getMinimumSize() { return new Dimension(0, 1); }
}
