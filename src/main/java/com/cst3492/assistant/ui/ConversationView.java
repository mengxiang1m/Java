package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatMessage;
import com.cst3492.assistant.chat.ChatReply;
import com.intellij.util.ui.JBUI;

import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Width-tracking vertical conversation; the composer is outside its viewport. */
final class ConversationView extends JPanel implements Scrollable {
    private final List<MessageCard> messages = new ArrayList<>();
    private final JPanel empty = ChatUi.card(ChatUi.SURFACE);

    ConversationView() {
        setLayout(new ChatColumnLayout());
        setBackground(ChatUi.BACKGROUND);
        setBorder(JBUI.Borders.empty(8));
        empty.setLayout(new BorderLayout(0, JBUI.scale(12)));
        WrappingLabel title = new WrappingLabel("✦  Your Java learning workspace");
        title.setForeground(ChatUi.TEXT);
        title.setFont(ChatUi.font().deriveFont(java.awt.Font.BOLD, ChatUi.font().getSize() + 2f));
        empty.add(title, BorderLayout.NORTH);
        WrappingLabel hint = new WrappingLabel("Ask a question, or attach selected Java code from the editor. "
                + "Answers and any supplied course sources will appear here.");
        hint.setForeground(ChatUi.MUTED);
        empty.add(hint, BorderLayout.CENTER);
        add(empty);
        getAccessibleContext().setAccessibleName("Conversation");
    }

    void append(ChatMessage message, ChatReply reply, Runnable retry) {
        if (messages.isEmpty()) { remove(empty); }
        MessageCard card = new MessageCard(message, reply, retry, ChatUi::copy);
        card.setAlignmentX(LEFT_ALIGNMENT);
        messages.add(card);
        add(card);
        revalidate();
        repaint();
    }

    List<MessageCard> messages() { return List.copyOf(messages); }
    boolean isEmptyStateVisible() { return messages.isEmpty(); }

    @Override
    public Dimension getPreferredScrollableViewportSize() { return new Dimension(360, 400); }
    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return JBUI.scale(20); }
    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(JBUI.scale(20), visible.height - JBUI.scale(40));
    }
    @Override
    public boolean getScrollableTracksViewportWidth() { return true; }
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && getPreferredSize().height < viewport.getHeight();
    }
}
