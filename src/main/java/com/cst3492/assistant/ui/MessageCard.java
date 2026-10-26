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
    private final javax.swing.Timer copyReset;
    private final JButton copyResponse;
    // This short label must size naturally; a width-constrained wrapping label
    // cannot grow its initially empty action area when feedback appears.
    private final javax.swing.JLabel copyFeedback = new javax.swing.JLabel("");
    private boolean disposed;

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
        JPanel header = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        header.setOpaque(false);
        header.add(heading, BorderLayout.CENTER);
        boolean canCopy = reply != null && reply.status() != ChatReply.Status.ERROR
                && message.role() == ChatMessage.Role.ASSISTANT;
        copyResponse = canCopy ? ChatUi.button("", false) : null;
        copyReset = new javax.swing.Timer(1600, event -> resetCopyFeedback());
        copyReset.setRepeats(false);
        if (copyResponse != null) {
            copyResponse.setIcon(com.intellij.icons.AllIcons.Actions.Copy);
            copyResponse.setToolTipText("Copy response");
            copyResponse.getAccessibleContext().setAccessibleName("Copy response");
            copyResponse.setPreferredSize(JBUI.size(28, 28));
            copyResponse.setBorder(JBUI.Borders.empty());
            copyResponse.putClientProperty("assistant.quiet", true);
            JPanel actions = new JPanel(new BorderLayout(JBUI.scale(4), 0));
            actions.setOpaque(false);
            copyFeedback.setForeground(ChatUi.MUTED);
            actions.add(copyFeedback, BorderLayout.CENTER);
            actions.add(copyResponse, BorderLayout.EAST);
            header.add(actions, BorderLayout.EAST);
            copyResponse.addActionListener(event -> {
                if (disposed) return;
                try {
                    // Use complete original Markdown, never the rendered or visible text.
                    copy.accept(reply.text());
                    copyResponse.setToolTipText("Copy response");
                    copyFeedback.setText("Copied");
                } catch (RuntimeException failure) {
                    copyFeedback.setText("Copy failed");
                    copyResponse.setToolTipText("Could not copy. Try again.");
                }
                if (isDisplayable()) copyReset.restart();
            });
        }
        surface.add(header, BorderLayout.NORTH);

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
    JButton copyResponseButton() { return copyResponse; }
    String copyFeedbackText() { return copyFeedback.getText(); }

    private void resetCopyFeedback() {
        if (disposed) return;
        copyFeedback.setText("");
        if (copyResponse != null) copyResponse.setToolTipText("Copy response");
    }

    void dispose() {
        disposed = true;
        copyReset.stop();
    }

    @Override public void removeNotify() {
        copyReset.stop();
        super.removeNotify();
    }

    @Override public void addNotify() {
        super.addNotify();
        resetCopyFeedback();
    }

    @Override
    public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    @Override
    public Dimension getMinimumSize() { return new Dimension(0, 1); }
}
