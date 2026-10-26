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
    // File selection actions are supplied by the panel; this class never performs I/O.
    private final JPanel attachmentActions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    private final JPanel attachments = new AttachmentList();
    private final JBScrollPane attachmentScroll;

    ChatComposer(JBTextArea input, JButton send) {
        super(new BorderLayout(0, JBUI.scale(6)));
        this.input = input;
        setOpaque(false);
        setBorder(JBUI.Borders.empty(10));
        input.setRows(2);
        input.setColumns(0);
        input.setOpaque(false);
        input.setBorder(JBUI.Borders.empty(2));
        input.getEmptyText().setText("Ask anything...");
        input.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) { input.getEmptyText().setText(""); }
            @Override public void focusLost(java.awt.event.FocusEvent event) { input.getEmptyText().setText("Ask anything..."); }
        });
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
        attachments.setOpaque(false);
        attachmentScroll = new JBScrollPane(attachments, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override public Dimension getPreferredSize() {
                return new Dimension(0, Math.min(JBUI.scale(96), attachments.getPreferredSize().height));
            }
        };
        attachmentScroll.setBorder(null);
        attachmentScroll.setOpaque(false);
        attachmentScroll.getViewport().setOpaque(false);
        attachmentScroll.setVisible(false);
        add(attachmentScroll, BorderLayout.NORTH);

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

    private static final class AttachmentList extends JPanel implements javax.swing.Scrollable {
        AttachmentList() { super(new AttachmentFlow()); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return JBUI.scale(24);
        }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return Math.max(JBUI.scale(24), visible.height);
        }
    }

    /** Width-aware wrapping, including preferred height, so the composer never grows sideways. */
    private static final class AttachmentFlow implements java.awt.LayoutManager {
        @Override public void addLayoutComponent(String name, java.awt.Component component) { }
        @Override public void removeLayoutComponent(java.awt.Component component) { }
        private Dimension arrange(java.awt.Container parent, boolean layout) {
            int gap = JBUI.scale(4);
            int available = parent.getWidth();
            if (parent.getParent() instanceof javax.swing.JViewport viewport) available = viewport.getWidth();
            available = Math.max(JBUI.scale(60), available);
            int x = 0, y = 0, height = 0;
            for (var child : parent.getComponents()) {
                Dimension size = child.getPreferredSize();
                int width = Math.min(available, Math.min(JBUI.scale(240), size.width));
                if (x > 0 && x + width > available) { x = 0; y += height + gap; height = 0; }
                if (layout) child.setBounds(x, y, width, size.height);
                x += width + gap;
                height = Math.max(height, size.height);
            }
            return new Dimension(available, y + height);
        }
        @Override public Dimension preferredLayoutSize(java.awt.Container parent) { return arrange(parent, false); }
        @Override public Dimension minimumLayoutSize(java.awt.Container parent) { return new Dimension(0, 0); }
        @Override public void layoutContainer(java.awt.Container parent) { arrange(parent, true); }
    }

    void addAttachmentAction(javax.swing.JComponent button) {
        attachmentActions.add(button);
        attachmentActions.setVisible(true);
    }

    void showAttachments(java.util.List<java.nio.file.Path> paths,
                         java.util.function.Consumer<java.nio.file.Path> remove, boolean enabled) {
        attachments.removeAll();
        for (var path : paths) {
            JPanel chip = new JPanel(new BorderLayout(JBUI.scale(4), 0)) {
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(ChatUi.CARD);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                    g.dispose();
                }
            };
            chip.setOpaque(false);
            chip.setBorder(JBUI.Borders.empty(2, 8));
            javax.swing.JLabel name = new javax.swing.JLabel(path.getFileName().toString(),
                    com.intellij.icons.AllIcons.FileTypes.Any_type, javax.swing.JLabel.LEADING);
            name.setForeground(ChatUi.TEXT);
            name.setToolTipText(path.toString());
            name.setMinimumSize(new Dimension(0, 0));
            chip.add(name, BorderLayout.CENTER);
            JButton close = ChatUi.button("×", false);
            close.setPreferredSize(new Dimension(JBUI.scale(24), JBUI.scale(24)));
            close.setBorder(JBUI.Borders.empty());
            close.setToolTipText("Remove attachment");
            close.setEnabled(enabled);
            close.addActionListener(event -> remove.accept(path));
            chip.add(close, BorderLayout.EAST);
            attachments.add(chip);
        }
        attachmentScroll.setVisible(!paths.isEmpty());
        revalidate();
        repaint();
    }

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
