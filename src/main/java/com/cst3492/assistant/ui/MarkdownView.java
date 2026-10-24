package com.cst3492.assistant.ui;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small native StyledDocument renderer. No HTML engine, URLs, images or remote content. */
final class MarkdownView extends JTextPane {
    private static final String CITATION = "course-source-reference";
    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[(\\d+)\\]");
    private final String original;
    private final Runnable showSources;
    private final SimpleAttributeSet normal = new SimpleAttributeSet();

    MarkdownView(String text, boolean markdown, Runnable showSources) {
        this.original = text;
        this.showSources = showSources;
        setEditorKit(new StyledEditorKit() {
            @Override
            public ViewFactory getViewFactory() {
                return element -> switch (element.getName()) {
                    case "content" -> new LabelView(element) {
                        @Override
                        public float getMinimumSpan(int axis) {
                            return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
                        }
                    };
                    case "paragraph" -> new ParagraphView(element) {
                        @Override
                        public void paint(java.awt.Graphics graphics, java.awt.Shape allocation) {
                            if (Boolean.TRUE.equals(getAttributes().getAttribute("code-block"))) {
                                var bounds = allocation.getBounds();
                                graphics.setColor(ChatUi.BACKGROUND);
                                graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                            }
                            super.paint(graphics, allocation);
                        }
                    };
                    case "section" -> new BoxView(element, View.Y_AXIS);
                    case "component" -> new ComponentView(element);
                    case "icon" -> new IconView(element);
                    default -> new LabelView(element);
                };
            }
        });
        setEditable(false);
        setOpaque(false);
        setForeground(ChatUi.TEXT);
        setFont(ChatUi.font());
        setBorder(null);
        setMargin(new Insets(0, 0, 0, 0));
        getAccessibleContext().setAccessibleName("Message content");
        StyleConstants.setFontFamily(normal, getFont().getFamily());
        StyleConstants.setFontSize(normal, getFont().getSize());
        StyleConstants.setForeground(normal, ChatUi.TEXT);
        if (markdown) {
            render(text);
        } else {
            add(text, normal);
        }
        setCaretPosition(0);
        MouseAdapter links = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                setCursor(Cursor.getPredefinedCursor(isCitationAt(viewToModel2D(event.getPoint()))
                        ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (isCitationAt(viewToModel2D(event.getPoint())) && MarkdownView.this.showSources != null) {
                    MarkdownView.this.showSources.run();
                }
            }
        };
        addMouseListener(links);
        addMouseMotionListener(links);
    }

    String originalText() { return original; }

    @Override
    public String getText() {
        // JTextPane's EditorKit serializes line endings on Windows. Read the native
        // document directly so copying/testing plain content does not normalize it.
        if (getDocument() == null) { return ""; }
        try {
            return getDocument().getText(0, getDocument().getLength());
        } catch (BadLocationException exception) {
            throw new IllegalStateException("Cannot read rendered message", exception);
        }
    }

    boolean isCitationAt(int offset) {
        return offset >= 0 && offset < getDocument().getLength()
                && Boolean.TRUE.equals(getStyledDocument().getCharacterElement(offset).getAttributes().getAttribute(CITATION));
    }

    private void render(String text) {
        boolean code = false;
        for (String line : text.split("\\R", -1)) {
            if (line.stripLeading().startsWith("```")) {
                code = !code;
                continue;
            }
            int start = getDocument().getLength();
            SimpleAttributeSet attributes = new SimpleAttributeSet(normal);
            String content = line;
            float gap = 5;
            if (code) {
                StyleConstants.setFontFamily(attributes, Font.MONOSPACED);
                StyleConstants.setBackground(attributes, ChatUi.BACKGROUND);
                gap = 0;
                add(content + "\n", attributes);
            } else {
                Matcher heading = Pattern.compile("^(#{1,6})\\s+(.+)$").matcher(line);
                if (heading.matches()) {
                    content = heading.group(2);
                    StyleConstants.setBold(attributes, true);
                    StyleConstants.setFontSize(attributes, getFont().getSize() + Math.max(1, 5 - heading.group(1).length()));
                    gap = 9;
                } else if (line.matches("^\\s*[-*+]\\s+.*")) {
                    content = line.replaceFirst("^\\s*[-*+]\\s+", "• ");
                } else if (line.startsWith("> ")) {
                    content = line.substring(2);
                    StyleConstants.setForeground(attributes, ChatUi.MUTED);
                }
                inline(content, attributes);
                add("\n", attributes);
            }
            SimpleAttributeSet paragraph = new SimpleAttributeSet();
            StyleConstants.setSpaceAbove(paragraph, 0);
            StyleConstants.setSpaceBelow(paragraph, gap);
            StyleConstants.setLineSpacing(paragraph, 0.12f);
            if (code) {
                paragraph.addAttribute("code-block", true);
                StyleConstants.setLeftIndent(paragraph, 8);
                StyleConstants.setRightIndent(paragraph, 8);
            }
            getStyledDocument().setParagraphAttributes(start, getDocument().getLength() - start, paragraph, false);
        }
    }

    private void inline(String text, SimpleAttributeSet attributes) {
        Matcher matcher = INLINE.matcher(text);
        int end = 0;
        while (matcher.find()) {
            add(text.substring(end, matcher.start()), attributes);
            SimpleAttributeSet styled = new SimpleAttributeSet(attributes);
            if (matcher.group(1) != null) {
                StyleConstants.setBold(styled, true);
                add(matcher.group(1), styled);
            } else if (matcher.group(2) != null) {
                StyleConstants.setFontFamily(styled, Font.MONOSPACED);
                StyleConstants.setBackground(styled, ChatUi.CARD);
                add(matcher.group(2), styled);
            } else {
                if (showSources != null) {
                    styled.addAttribute(CITATION, true);
                    StyleConstants.setForeground(styled, ChatUi.ACCENT);
                    StyleConstants.setUnderline(styled, true);
                }
                add(matcher.group(), styled);
            }
            end = matcher.end();
        }
        add(text.substring(end), attributes);
    }

    private void add(String text, SimpleAttributeSet attributes) {
        try {
            getStyledDocument().insertString(getDocument().getLength(), text, attributes);
        } catch (BadLocationException exception) {
            throw new IllegalStateException("Cannot render message", exception);
        }
    }

    @Override
    public boolean getScrollableTracksViewportWidth() { return true; }

    @Override
    public Dimension getMinimumSize() { return new Dimension(0, 1); }

    @Override
    public Dimension getPreferredSize() {
        int width = getParent() == null || getParent().getWidth() == 0 ? 320
                : getParent().getWidth() - getParent().getInsets().left - getParent().getInsets().right;
        Insets insets = getInsets();
        View view = getUI().getRootView(this);
        view.setSize(Math.max(1, width - insets.left - insets.right), Integer.MAX_VALUE);
        return new Dimension(Math.max(1, width), (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS))
                + insets.top + insets.bottom);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean changed = width != getWidth();
        super.setBounds(x, y, width, height);
        if (changed) { revalidate(); }
    }
}
