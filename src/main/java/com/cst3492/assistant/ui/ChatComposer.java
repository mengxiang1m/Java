package com.cst3492.assistant.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.View;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/** One visual input surface. Owns layout only; sending/lifecycle remain in AssistantChatPanel. */
final class ChatComposer extends JPanel {
    private final JBTextArea input;
    private final JBScrollPane inputScroll;
    // No fake upload action: A's ingestion/context and size policy must exist first.
    private final JPanel attachmentActions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));

    ChatComposer(JBTextArea input, JButton send) {
        super(new BorderLayout(0, JBUI.scale(6)));
        this.input = input;
        setOpaque(false);
        setBorder(JBUI.Borders.empty(10));
        input.setRows(2);
        input.setColumns(0);
        input.setOpaque(false);
        input.setBorder(JBUI.Borders.empty(2));
        inputScroll = new JBScrollPane(input, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override
            public Dimension getPreferredSize() {
                int width = Math.max(JBUI.scale(80), ChatComposer.this.getWidth()
                        - ChatComposer.this.getInsets().left - ChatComposer.this.getInsets().right);
                Insets inputInsets = input.getInsets();
                Insets scrollInsets = getInsets();
                int scrollbarWidth = getVerticalScrollBar().isVisible() ? getVerticalScrollBar().getPreferredSize().width : 0;
                View root = input.getUI().getRootView(input);
                root.setSize(Math.max(1, width - inputInsets.left - inputInsets.right - scrollbarWidth), Integer.MAX_VALUE);
                int lineHeight = input.getFontMetrics(input.getFont()).getHeight();
                int padding = inputInsets.top + inputInsets.bottom + scrollInsets.top + scrollInsets.bottom;
                int contentHeight = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + padding;
                return new Dimension(width, Math.max(lineHeight * 2 + padding,
                        Math.min(lineHeight * 7 + padding, contentHeight)));
            }
        };
        inputScroll.setOpaque(false);
        inputScroll.getViewport().setOpaque(false);
        inputScroll.setBorder(null);
        inputScroll.setMinimumSize(new Dimension(0, JBUI.scale(28)));
        add(inputScroll, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        attachmentActions.setOpaque(false);
        attachmentActions.setVisible(false);
        toolbar.add(attachmentActions, BorderLayout.WEST);
        toolbar.add(send, BorderLayout.EAST);
        add(toolbar, BorderLayout.SOUTH);

        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { revalidate(); }
            @Override public void removeUpdate(DocumentEvent event) { revalidate(); }
            @Override public void changedUpdate(DocumentEvent event) { revalidate(); }
        });
        input.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) { repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent event) { repaint(); }
        });
    }

    JBScrollPane inputScroll() { return inputScroll; }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int radius = JBUI.scale(22);
        g.setColor(ChatUi.SURFACE);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
        g.setColor(input.isFocusOwner() ? ChatUi.ACCENT : ChatUi.BORDER);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        g.dispose();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean changed = width != getWidth();
        super.setBounds(x, y, width, height);
        if (changed) { revalidate(); }
    }
}
