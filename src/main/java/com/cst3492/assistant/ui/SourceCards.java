package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.CourseSource;
import com.intellij.util.ui.JBUI;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;
import java.util.function.Consumer;

/** Collapsible metadata only. Excerpts, scores and document destinations are not in CourseSource. */
final class SourceCards extends JPanel {
    private final JButton toggle;
    private final JPanel items = new JPanel();
    private final List<SourceCard> cards;

    SourceCards(List<CourseSource> sources, Consumer<String> copy) {
        super(new BorderLayout(0, JBUI.scale(8)));
        setOpaque(false);
        toggle = ChatUi.button("▸ Sources (" + sources.size() + ")", false);
        toggle.setHorizontalAlignment(JButton.LEADING);
        toggle.setToolTipText("Supplied source metadata; citation IDs are not available for per-source matching");
        add(toggle, BorderLayout.NORTH);
        items.setOpaque(false);
        items.setLayout(new ChatColumnLayout());
        cards = sources.stream().map(source -> new SourceCard(source, copy)).toList();
        for (SourceCard card : cards) {
            items.add(card);
        }
        items.setVisible(false);
        add(items, BorderLayout.CENTER);
        toggle.addActionListener(event -> setExpanded(!items.isVisible()));
    }

    void setExpanded(boolean expanded) {
        items.setVisible(expanded);
        toggle.setText((expanded ? "▾" : "▸") + " Sources (" + cards.size() + ")");
        revalidate();
        repaint();
    }

    boolean isExpanded() { return items.isVisible(); }
    List<SourceCard> cards() { return cards; }

    static final class SourceCard extends JPanel {
        private final CourseSource source;
        private final JPanel details = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        private final JButton toggle;

        SourceCard(CourseSource source, Consumer<String> copy) {
            super(new BorderLayout(0, JBUI.scale(6)));
            this.source = source;
            setOpaque(false);
            setBorder(JBUI.Borders.emptyBottom(8));
            JPanel surface = ChatUi.card(ChatUi.BACKGROUND);
            surface.setLayout(new BorderLayout(0, JBUI.scale(6)));
            MarkdownView title = new MarkdownView(source.documentName(), false, null);
            javax.swing.text.SimpleAttributeSet bold = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setBold(bold, true);
            title.getStyledDocument().setCharacterAttributes(0, title.getDocument().getLength(), bold, false);
            surface.add(title, BorderLayout.NORTH);
            String location = (source.type() == CourseSource.Type.PDF_PAGE ? "PDF · Page " : "PPT · Slide ") + source.number();
            JPanel summary = new JPanel(new BorderLayout(0, JBUI.scale(4)));
            summary.setOpaque(false);
            WrappingLabel metadata = new WrappingLabel(location + (source.testData() ? " · Test data, not evidence" : ""));
            metadata.setForeground(source.testData() ? ChatUi.ACCENT : ChatUi.MUTED);
            summary.add(metadata, BorderLayout.NORTH);
            toggle = ChatUi.button("▸ Details", false);
            toggle.addActionListener(event -> setExpanded(!details.isVisible()));
            summary.add(toggle, BorderLayout.SOUTH);
            surface.add(summary, BorderLayout.CENTER);
            details.setOpaque(false);
            WrappingLabel note = new WrappingLabel("Source metadata only. No excerpt, relevance score or document destination was supplied.");
            note.setForeground(ChatUi.MUTED);
            details.add(note, BorderLayout.CENTER);
            JButton copyButton = ChatUi.button("Copy citation", false);
            copyButton.addActionListener(event -> copy.accept(source.documentName() + " — " + location
                    + (source.testData() ? " [TEST DATA - not evidence]" : "")));
            details.add(copyButton, BorderLayout.SOUTH);
            details.setVisible(false);
            surface.add(details, BorderLayout.SOUTH);
            add(surface, BorderLayout.CENTER);
        }

        CourseSource source() { return source; }
        boolean isExpanded() { return details.isVisible(); }

        void setExpanded(boolean expanded) {
            details.setVisible(expanded);
            toggle.setText((expanded ? "▾" : "▸") + " Details");
            revalidate();
            repaint();
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }
}
