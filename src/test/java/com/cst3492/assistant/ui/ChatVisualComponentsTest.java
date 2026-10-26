package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatMessage;
import com.cst3492.assistant.chat.ChatReply;
import com.cst3492.assistant.chat.CourseSource;
import com.cst3492.assistant.chat.MockChatService;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.StyleConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ChatVisualComponentsTest {
    @Test
    void attachmentPillsShareRowsAndWrapWhenComposerNarrows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var composer = new ChatComposer(new com.intellij.ui.components.JBTextArea(), ChatUi.sendButton());
            composer.showAttachments(List.of(java.nio.file.Path.of("a.pdf"), java.nio.file.Path.of("b.pptx")),
                    ignored -> { }, true);
            var pills = buttons(composer).stream().filter(b -> "×".equals(b.getText()))
                    .map(Component::getParent).toList();
            for (int width : new int[]{500, 130}) {
                composer.setSize(width, 240);
                for (int pass = 0; pass < 4; pass++) {
                    composer.doLayout();
                    all(composer).stream().filter(c -> c instanceof Container)
                            .forEach(c -> ((Container) c).doLayout());
                }
                if (width == 500) assertEquals(pills.get(0).getY(), pills.get(1).getY());
                else assertTrue(pills.get(1).getY() > pills.get(0).getY());
            }
        });
    }
    @Test
    void narrowComposerCapsAttachmentHeightAndKeepsRemovalInsideViewport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var input = new com.intellij.ui.components.JBTextArea();
            input.setLineWrap(true);
            var composer = new ChatComposer(input, ChatUi.sendButton());
            var paths = java.util.stream.IntStream.range(0, 10).mapToObj(i -> java.nio.file.Path.of(
                    "very_long_course_filename_".repeat(8) + i + ".pdf")).toList();
            composer.showAttachments(paths, ignored -> { }, true);
            composer.setSize(220, composer.getPreferredSize().height);
            for (int i = 0; i < 3; i++) {
                composer.doLayout();
                all(composer).stream().filter(c -> c instanceof Container).forEach(c -> ((Container) c).doLayout());
            }
            assertTrue(composer.inputScroll().getHeight() >= 28);
            var scroll = all(composer).stream()
                    .filter(c -> c instanceof javax.swing.JScrollPane s && s != composer.inputScroll())
                    .map(c -> (javax.swing.JScrollPane) c).findFirst().orElseThrow();
            assertTrue(scroll.getHeight() <= com.intellij.util.ui.JBUI.scale(96));
            assertEquals(scroll.getViewport().getWidth(), scroll.getViewport().getView().getWidth());
            assertEquals(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
            assertTrue(buttons(composer).stream().filter(b -> "×".equals(b.getText())).allMatch(b -> b.getParent().getWidth() <= 220));
            composer.showAttachments(List.of(), ignored -> { }, true);
            assertFalse(scroll.isVisible());
        });
    }
    @org.junit.jupiter.api.BeforeAll
    static void enablePlatformIconRendering() {
        // Plain unit tests do not run IDE startup, which normally activates IconLoader.
        com.intellij.openapi.util.IconLoader.activate();
    }

    @org.junit.jupiter.api.AfterAll
    static void resetPlatformIconRendering() {
        com.intellij.openapi.util.IconLoader.deactivate();
    }

    @Test
    void userTextIsPreservedAndAssistantHasSeparateSubtleStatus() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConversationView conversation = new ConversationView();
            assertTrue(conversation.isEmptyStateVisible());
            String question = "  **not Markdown**\n  int x;  ";
            conversation.append(new ChatMessage(ChatMessage.Role.USER, question), null, null);
            ChatReply reply = new MockChatService().reply("Question");
            conversation.append(ChatReplyPresentation.cardMessage(reply), reply, null);
            assertFalse(conversation.isEmptyStateVisible());
            assertEquals(2, conversation.messages().size());
            MessageCard user = conversation.messages().get(0);
            MessageCard assistant = conversation.messages().get(1);
            assertEquals(question, user.body().getText());
            assertTrue(user.getInsets().left > assistant.getInsets().left);
            assertEquals(ChatMessage.Role.ASSISTANT, assistant.message().role());
            assertEquals(ChatReply.Status.MOCK, assistant.reply().status());
            assertFalse(assistant.body().getText().contains("[MOCK"));
            assertTrue(labels(assistant).stream().anyMatch(text -> text.contains("Mock / test data")));
        });
    }

    @Test
    void nativeMarkdownStylesHeadingsBoldListsInlineAndFencedCode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MarkdownView view = new MarkdownView("# Heading\n**bold** and `inline`\n- bullet\n1. numbered\n```java\n  int x;\n```", true, null);
            String rendered = view.getText();
            assertTrue(rendered.contains("Heading"));
            assertFalse(rendered.contains("# Heading"));
            assertTrue(rendered.contains("• bullet"));
            assertTrue(rendered.contains("1. numbered"));
            assertTrue(rendered.contains("  int x;"));
            assertTrue(StyleConstants.isBold(view.getStyledDocument().getCharacterElement(rendered.indexOf("bold")).getAttributes()));
            assertEquals(Font.MONOSPACED, StyleConstants.getFontFamily(
                    view.getStyledDocument().getCharacterElement(rendered.indexOf("int x")).getAttributes()));
            assertEquals(Font.MONOSPACED, StyleConstants.getFontFamily(
                    view.getStyledDocument().getCharacterElement(rendered.indexOf("inline")).getAttributes()));
        });
    }

    @Test
    void longAnswerAndUnbrokenCodeWrapAtNarrowWidths() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel parent = new JPanel(new BorderLayout());
            String text = "A long paragraph with readable spacing. ".repeat(15)
                    + "\n```java\n" + "veryLongIdentifier".repeat(30) + "\n```";
            MarkdownView view = new MarkdownView(text, true, null);
            parent.add(view);
            parent.setSize(600, 600);
            Dimension wide = view.getPreferredSize();
            parent.setSize(180, 600);
            Dimension narrow = view.getPreferredSize();
            assertTrue(narrow.height > wide.height);
            assertTrue(narrow.width <= 180);
            assertTrue(view.getScrollableTracksViewportWidth());
            assertEquals(0, view.getMinimumSize().width);
            assertEquals(text, view.originalText());
        });
    }

    @Test
    void sourcesExpandCollapseAndCopyWithoutChangingDataOrInventingMetadata() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CourseSource source = new CourseSource("lecture.pdf", CourseSource.Type.PDF_PAGE, 3, true);
            List<String> copied = new ArrayList<>();
            SourceCards sources = new SourceCards(List.of(source), copied::add);
            assertFalse(sources.isExpanded());
            sources.setExpanded(true);
            SourceCards.SourceCard card = sources.cards().get(0);
            card.setExpanded(true);
            assertTrue(card.isExpanded());
            buttons(card).stream().filter(button -> button.getText().equals("Copy citation")).findFirst().orElseThrow().doClick();
            assertTrue(copied.get(0).contains("Page 3"));
            assertTrue(copied.get(0).contains("TEST DATA"));
            card.setExpanded(false);
            sources.setExpanded(false);
            sources.setExpanded(true);
            assertSame(source, card.source());
            assertFalse(buttons(card).stream().anyMatch(button -> button.getText().contains("Open")));
            assertTrue(labels(card).stream().noneMatch(label -> label.matches(".*\\d+%.*")));
        });
    }

    @Test
    void longFilenamesWrapNativelyAndManySourcesRemainInOrder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChatReply reply = new MockChatService().reply("/many-sources");
            SourceCards cards = new SourceCards(reply.sources(), text -> { });
            cards.setExpanded(true);
            assertEquals(reply.sources(), cards.cards().stream().map(SourceCards.SourceCard::source).toList());
            assertEquals(30, cards.cards().size());
            MarkdownView filename = all(cards.cards().get(0)).stream()
                    .filter(component -> component instanceof MarkdownView view
                            && view.originalText().equals(reply.sources().get(0).documentName()))
                    .map(component -> (MarkdownView) component).findFirst().orElseThrow();
            filename.getParent().setSize(600, 200);
            int wideHeight = filename.getPreferredSize().height;
            filename.getParent().setSize(170, 200);
            assertTrue(filename.getPreferredSize().width <= 170);
            assertTrue(filename.getPreferredSize().height > wideHeight);
        });
    }

    @Test
    void noSourcesAndGeneralKnowledgeHaveNoSourceControlsOrDiagnosticPrefixes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (ChatReply reply : List.of(new MockChatService().reply("/no-sources"),
                    new ChatReply("Response is based on general knowledge; no specific course material is referenced.",
                            ChatReply.Status.NORMAL, List.of()))) {
                MessageCard card = new MessageCard(ChatReplyPresentation.cardMessage(reply), reply, null, text -> { });
                assertNull(card.sources());
                assertTrue(card.body().getText().contains(reply.text()));
                assertFalse(card.body().getText().contains("[NORMAL]"));
                assertFalse(buttons(card).stream().anyMatch(button -> button.getText().contains("Sources")));
            }
        });
    }

    @Test
    void inlineCitationIsClickableOnlyWithSuppliedSourcesAndNeverInventsADocumentMapping() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean opened = new AtomicBoolean();
            MarkdownView linked = new MarkdownView("Answer [42]", true, () -> opened.set(true));
            assertTrue(linked.isCitationAt(linked.getText().indexOf("[42]")));
            linked.setSize(300, 100);
            try {
                var bounds = linked.modelToView2D(linked.getText().indexOf("[42]") + 1);
                MouseEvent click = new MouseEvent(linked, MouseEvent.MOUSE_CLICKED, 0, 0,
                        (int) bounds.getX() + 1, (int) bounds.getY() + 2, 1, false);
                for (var listener : linked.getMouseListeners()) { listener.mouseClicked(click); }
            } catch (javax.swing.text.BadLocationException exception) { throw new AssertionError(exception); }
            assertTrue(opened.get());
            MarkdownView unlinked = new MarkdownView("Answer [42]", true, null);
            assertFalse(unlinked.isCitationAt(unlinked.getText().indexOf("[42]")));
        });
    }

    @Test
    void errorHasRetryActionWithoutTreatingErrorAsNormalAnswer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean retried = new AtomicBoolean();
            ChatReply reply = new MockChatService().reply("/error");
            MessageCard card = new MessageCard(ChatReplyPresentation.cardMessage(reply), reply,
                    () -> retried.set(true), text -> { });
            assertEquals(ChatMessage.Role.ERROR, card.message().role());
            assertNull(card.sources());
            buttons(card).stream().filter(button -> button.getText().equals("Retry question")).findFirst().orElseThrow().doClick();
            assertTrue(retried.get());
        });
    }

    @Test
    void htmlAndRemoteImageTextAreNeverExecutedOrLoaded() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MarkdownView view = new MarkdownView("<script>alert(1)</script> ![image](https://invalid.example/image.png)", true, null);
            assertTrue(view.getText().contains("<script>"));
            assertTrue(view.getText().contains("https://invalid.example"));
            assertFalse(view.getEditorKit().getContentType().contains("html"));
        });
    }

    private static List<Component> all(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            result.add(child);
            if (child instanceof Container container) { result.addAll(all(container)); }
        }
        return result;
    }

    @Test
    void sendIconPaintHasDistinctEnabledHoverPressedAndDisabledStates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton send = ChatUi.sendButton();
            send.setSize(send.getPreferredSize());
            assertTrue(send.getIcon().getIconWidth() > 0);
            var iconImage = new java.awt.image.BufferedImage(send.getWidth(), send.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            var iconGraphics = iconImage.createGraphics();
            send.paint(iconGraphics);
            iconGraphics.dispose();
            int glyphPixels = 0;
            int surface = iconImage.getRGB(5, send.getHeight() / 2);
            for (int y = 10; y < 28; y++) {
                for (int x = 12; x < 26; x++) {
                    if (iconImage.getRGB(x, y) != surface) { glyphPixels++; }
                }
            }
            assertTrue(glyphPixels > 4, "The upward arrow must be visibly painted, not just a blue circle");
            assertNotEquals(surface, iconImage.getRGB(send.getWidth() / 2, send.getHeight() / 2 + 5),
                    "Use an upward arrow with a shaft, not a disclosure triangle");
            int normal = buttonPixel(send);
            send.getModel().setRollover(true);
            int hover = buttonPixel(send);
            send.getModel().setArmed(true);
            send.getModel().setPressed(true);
            int pressed = buttonPixel(send);
            send.setEnabled(false);
            int disabled = buttonPixel(send);
            assertNotEquals(normal, hover);
            assertNotEquals(normal, pressed);
            assertNotEquals(normal, disabled);
        });
    }

    private static int buttonPixel(JButton button) {
        var image = new java.awt.image.BufferedImage(button.getWidth(), button.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        button.paint(graphics);
        graphics.dispose();
        return image.getRGB(5, button.getHeight() / 2);
    }


    private static List<JButton> buttons(Container root) {
        return all(root).stream().filter(component -> component instanceof JButton).map(component -> (JButton) component).toList();
    }

    private static List<String> labels(Container root) {
        return all(root).stream().filter(component -> component instanceof WrappingLabel)
                .map(component -> ((WrappingLabel) component).getText()).toList();
    }
}
