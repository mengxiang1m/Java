package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatService;
import com.cst3492.assistant.chat.MockChatService;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AssistantChatPanelLifecycleTest {
    @org.junit.jupiter.api.BeforeAll
    static void enablePlatformIconRendering() {
        com.intellij.openapi.util.IconLoader.activate();
    }

    @org.junit.jupiter.api.AfterAll
    static void resetPlatformIconRendering() {
        com.intellij.openapi.util.IconLoader.deactivate();
    }

    private Fixture create(ChatService service) throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
                new Class<?>[]{Project.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isDisposed" -> closed.get();
                    case "getName", "toString" -> "Lifecycle test project";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        AtomicReference<AssistantChatPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new AssistantChatPanel(project, service)));
        return new Fixture(panel.get(), closed);
    }

    private record Fixture(AssistantChatPanel panel, AtomicBoolean closed) { }

    private ChatRequestWorker start(Fixture fixture, CountDownLatch done) throws Exception {
        AtomicReference<ChatRequestWorker> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JTextArea input = (JTextArea) fixture.panel.getInput();
            input.setText("Question");
            input.getActionMap().get("sendQuestion").actionPerformed(new ActionEvent(input, 0, "send"));
            ChatRequestWorker worker = field(fixture.panel, "worker", ChatRequestWorker.class);
            worker.addPropertyChangeListener(event -> {
                if (event.getNewValue() == SwingWorker.StateValue.DONE) {
                    done.countDown();
                }
            });
            result.set(worker);
            assertTrue(field(fixture.panel, "sending", Boolean.class));
            assertFalse(field(fixture.panel, "send", JButton.class).isEnabled());
            assertFalse(input.isEditable());
        });
        return result.get();
    }

    @Test
    void failureRestoresActualComposerAndClearsWorker() throws Exception {
        Fixture fixture = create(request -> { throw new IllegalStateException("Test failure"); });
        CountDownLatch done = new CountDownLatch(1);
        start(fixture, done);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertIdle(fixture);
            assertTrue(field(fixture.panel, "conversation", ConversationView.class).messages().stream()
                    .anyMatch(card -> card.reply() != null
                            && card.reply().status() == com.cst3492.assistant.chat.ChatReply.Status.ERROR));
            fixture.panel.dispose();
        });
    }

    @Test
    void closedProjectStillClearsBookkeepingWithoutRestoringUi() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Fixture fixture = create(request -> {
            entered.countDown();
            await(release);
            return new MockChatService().reply(request);
        });
        CountDownLatch done = new CountDownLatch(1);
        start(fixture, done);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> fixture.closed.set(true));
        release.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertNull(field(fixture.panel, "worker", ChatRequestWorker.class));
            assertFalse(field(fixture.panel, "sending", Boolean.class));
            assertFalse(field(fixture.panel, "send", JButton.class).isEnabled());
            assertFalse(((JTextArea) fixture.panel.getInput()).isEditable());
            assertTrue(field(fixture.panel, "conversation", ConversationView.class).messages().stream()
                    .noneMatch(card -> card.reply() != null));
            fixture.panel.dispose();
        });
    }

    @Test
    void disposalDuringButtonRestorationDoesNotTouchInputAfterDisposal() throws Exception {
        Fixture fixture = create(new MockChatService());
        SwingUtilities.invokeAndWait(() -> field(fixture.panel, "send", JButton.class)
                .addPropertyChangeListener("enabled", event -> {
                    if (Boolean.TRUE.equals(event.getNewValue())) {
                        fixture.panel.dispose();
                    }
                }));
        CountDownLatch done = new CountDownLatch(1);
        start(fixture, done);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertNull(field(fixture.panel, "worker", ChatRequestWorker.class));
            assertFalse(field(fixture.panel, "sending", Boolean.class));
            assertFalse(((JTextArea) fixture.panel.getInput()).isEditable());
        });
    }

    @Test
    void offEdtDisposalCancelsAndClearsStateWithoutRestoringComponents() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        Fixture fixture = create(request -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return new MockChatService().reply(request);
        });
        CountDownLatch done = new CountDownLatch(1);
        ChatRequestWorker worker = start(fixture, done);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        fixture.panel.dispose(); // deliberately not the EDT
        assertTrue(done.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(worker.isCancelled());
            assertNull(field(fixture.panel, "worker", ChatRequestWorker.class));
            assertFalse(field(fixture.panel, "sending", Boolean.class));
            assertFalse(field(fixture.panel, "send", JButton.class).isEnabled());
            assertFalse(((JTextArea) fixture.panel.getInput()).isEditable());
        });
    }

    private void assertIdle(Fixture fixture) {
        assertNull(field(fixture.panel, "worker", ChatRequestWorker.class));
        assertFalse(field(fixture.panel, "sending", Boolean.class));
        assertTrue(field(fixture.panel, "send", JButton.class).isEnabled());
        assertTrue(((JTextArea) fixture.panel.getInput()).isEditable());
    }

    @Test
    void staleFinalizerCannotClearOrEnableANewerRequest() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        Fixture fixture = create(request -> {
            if (calls.incrementAndGet() == 2) {
                await(release);
            }
            return new MockChatService().reply(request);
        });
        CountDownLatch firstDone = new CountDownLatch(1);
        ChatRequestWorker first = start(fixture, firstDone);
        assertTrue(firstDone.await(5, TimeUnit.SECONDS));
        CountDownLatch secondDone = new CountDownLatch(1);
        ChatRequestWorker second = start(fixture, secondDone);
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var finish = AssistantChatPanel.class.getDeclaredMethod("finishSending", ChatRequestWorker.class);
                    finish.setAccessible(true);
                    finish.invoke(fixture.panel, first);
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
                assertSame(second, field(fixture.panel, "worker", ChatRequestWorker.class));
                assertTrue(field(fixture.panel, "sending", Boolean.class));
                assertFalse(field(fixture.panel, "send", JButton.class).isEnabled());
                assertFalse(((JTextArea) fixture.panel.getInput()).isEditable());
            });
        } finally {
            release.countDown();
        }
        assertTrue(secondDone.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertIdle(fixture);
            fixture.panel.dispose();
        });
    }

    @Test
    void reentrantSendDuringRestorationIsIgnoredThenNextRequestWorks() throws Exception {
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        AtomicBoolean restarted = new AtomicBoolean();
        Fixture fixture = create(request -> {
            if (calls.incrementAndGet() == 2) {
                secondEntered.countDown();
                await(release);
            }
            return new MockChatService().reply(request);
        });
        SwingUtilities.invokeAndWait(() -> field(fixture.panel, "send", JButton.class)
                .addPropertyChangeListener("enabled", event -> {
                    if (Boolean.TRUE.equals(event.getNewValue()) && restarted.compareAndSet(false, true)) {
                        JTextArea input = (JTextArea) fixture.panel.getInput();
                        input.setText("Next question");
                        input.getActionMap().get("sendQuestion").actionPerformed(new ActionEvent(input, 0, "send"));
                    }
                }));
        CountDownLatch firstDone = new CountDownLatch(1);
        start(fixture, firstDone);
        assertTrue(firstDone.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertIdle(fixture);
            assertEquals(1, calls.get());
        });
        start(fixture, secondDone);
        try {
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(field(fixture.panel, "sending", Boolean.class));
                assertFalse(field(fixture.panel, "send", JButton.class).isEnabled());
                assertFalse(((JTextArea) fixture.panel.getInput()).isEditable());
            });
        } finally {
            release.countDown();
        }
        assertTrue(secondDone.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertIdle(fixture);
            fixture.panel.dispose();
        });
    }

    private static <T> T field(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void editorContextReplaceAndClearPreserveDraftAndExactCode() throws Exception {
        Fixture fixture = create(new MockChatService());
        SwingUtilities.invokeAndWait(() -> {
            JTextArea input = (JTextArea) fixture.panel.getInput();
            input.setText("Keep my draft\nSecond line");
            fixture.panel.setEditorContext("\tint first;\r\n");
            assertEquals("\tint first;\r\n", field(fixture.panel, "editorContext", String.class));
            fixture.panel.setEditorContext("  int second;\n");
            assertEquals("  int second;\n", field(fixture.panel, "contextPreview", JTextArea.class).getText());
            assertEquals("Keep my draft\nSecond line", input.getText());
            findButton(fixture.panel, "Clear context").doClick();
            assertEquals("", field(fixture.panel, "editorContext", String.class));
            assertFalse(field(fixture.panel, "contextPanel", javax.swing.JPanel.class).isVisible());
            assertEquals("Keep my draft\nSecond line", input.getText());
            fixture.panel.dispose();
        });
    }

    @Test
    void loadingCancellationAndErrorRetryUseTheOriginalRequestSnapshot() throws Exception {
        java.util.List<com.cst3492.assistant.chat.ChatRequest> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        Fixture fixture = create(request -> {
            requests.add(request);
            if (requests.size() == 1) { throw new IllegalStateException("Simulated failure"); }
            return new MockChatService().reply(request);
        });
        SwingUtilities.invokeAndWait(() -> fixture.panel.setEditorContext("int original;\n"));
        CountDownLatch firstDone = new CountDownLatch(1);
        start(fixture, firstDone);
        assertTrue(firstDone.await(5, TimeUnit.SECONDS));
        CountDownLatch retried = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(field(fixture.panel, "loading", javax.swing.JProgressBar.class).isVisible());
            fixture.panel.setEditorContext("int newerSelection;\n");
            findButton(fixture.panel, "Retry question").doClick();
            ChatRequestWorker worker = field(fixture.panel, "worker", ChatRequestWorker.class);
            assertTrue(field(fixture.panel, "loading", javax.swing.JProgressBar.class).isVisible());
            worker.addPropertyChangeListener(event -> {
                if (event.getNewValue() == SwingWorker.StateValue.DONE) { retried.countDown(); }
            });
        });
        assertTrue(retried.await(5, TimeUnit.SECONDS));
        assertEquals(2, requests.size());
        assertEquals(requests.get(0), requests.get(1));
        SwingUtilities.invokeAndWait(() -> {
            assertIdle(fixture);
            assertFalse(field(fixture.panel, "loading", javax.swing.JProgressBar.class).isVisible());
            fixture.panel.dispose();
        });

        CountDownLatch entered = new CountDownLatch(1);
        Fixture cancelled = create(request -> {
            entered.countDown();
            try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            return new MockChatService().reply(request);
        });
        CountDownLatch done = new CountDownLatch(1);
        ChatRequestWorker worker = start(cancelled, done);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> worker.cancel(true));
        assertTrue(done.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertIdle(cancelled);
            assertFalse(field(cancelled.panel, "loading", javax.swing.JProgressBar.class).isVisible());
            assertTrue(field(cancelled.panel, "status", WrappingLabel.class).getText().contains("cancelled"));
            cancelled.panel.dispose();
        });
    }

    @Test
    void completePanelKeepsComposerAndConversationIndependentAtNarrowAndWideWidths() throws Exception {
        AtomicBoolean previousDark = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            previousDark.set(!com.intellij.ui.JBColor.isBright());
            com.intellij.ui.JBColor.setDark(true);
        });
        try {
        Fixture fixture = create(new MockChatService());
        SwingUtilities.invokeAndWait(() -> {
            fixture.panel.setEditorContext("    veryLongIdentifier".repeat(15) + "\n    int next;\n");
            ConversationView conversation = field(fixture.panel, "conversation", ConversationView.class);
            conversation.append(new com.cst3492.assistant.chat.ChatMessage(
                    com.cst3492.assistant.chat.ChatMessage.Role.USER, "Explain the Java code."), null, null);
            var mock = new MockChatService().reply("/sources");
            var reply = new com.cst3492.assistant.chat.ChatReply("## Understanding the code\n"
                    + "The **selected code** demonstrates a Java concept [1].\n\n"
                    + "- Review the declaration\n- Check the initialization\n\n"
                    + "```java\n    int count = 3;\n    System.out.println(count);\n```\n"
                    + "These source records are test fixtures, not course evidence.", mock.status(), mock.sources());
            conversation.append(ChatReplyPresentation.cardMessage(reply), reply, null);
            conversation.messages().get(1).sources().setExpanded(true);
            conversation.messages().get(1).sources().cards().get(0).setExpanded(true);
            for (int width : new int[]{340, 760, 340}) {
                fixture.panel.setSize(width, 1000);
                for (int pass = 0; pass < 8; pass++) { layoutTree(fixture.panel); }
                var scroll = field(fixture.panel, "conversationScroll", javax.swing.JScrollPane.class);
                assertFalse(scroll.getHorizontalScrollBar().isVisible());
                assertEquals(scroll.getViewport().getExtentSize().width, conversation.getWidth());
                JButton send = field(fixture.panel, "send", JButton.class);
                java.awt.Rectangle sendBounds = SwingUtilities.convertRectangle(send.getParent(), send.getBounds(), fixture.panel);
                assertTrue(sendBounds.x >= 0 && sendBounds.x + sendBounds.width <= width);
                assertTrue(sendBounds.y + sendBounds.height <= fixture.panel.getHeight());
                var input = fixture.panel.getInput();
                assertFalse(SwingUtilities.isDescendingFrom(input, scroll));
                assertTrue(input.getWidth() > 100);
                assertTrue(conversation.getHeight() > 0);
                try {
                    var image = new java.awt.image.BufferedImage(width, 1000, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    fixture.panel.printAll(graphics);
                    graphics.dispose();
                    var output = java.nio.file.Path.of("build", "reports", "ui", "assistant-" + width + ".png");
                    java.nio.file.Files.createDirectories(output.getParent());
                    javax.imageio.ImageIO.write(image, "png", output.toFile());
                } catch (java.io.IOException exception) { throw new AssertionError(exception); }
            }
            fixture.panel.dispose();
        });
        } finally {
            SwingUtilities.invokeAndWait(() -> com.intellij.ui.JBColor.setDark(previousDark.get()));
        }
    }

    private static JButton findButton(java.awt.Container root, String text) {
        for (java.awt.Component component : root.getComponents()) {
            if (component instanceof JButton button && button.getText().equals(text)) { return button; }
            if (component instanceof java.awt.Container container) {
                JButton match = findButtonOrNull(container, text);
                if (match != null) { return match; }
            }
        }
        throw new AssertionError("Button not found: " + text);
    }

    private static JButton findButtonOrNull(java.awt.Container root, String text) {
        for (java.awt.Component component : root.getComponents()) {
            if (component instanceof JButton button && button.getText().equals(text)) { return button; }
            if (component instanceof java.awt.Container container) {
                JButton match = findButtonOrNull(container, text);
                if (match != null) { return match; }
            }
        }
        return null;
    }

    private static void layoutTree(java.awt.Container parent) {
        parent.doLayout();
        for (java.awt.Component component : parent.getComponents()) {
            if (component instanceof java.awt.Container container) { layoutTree(container); }
        }
    }

    @Test
    void enterSendsShiftEnterInsertsAtCaretAndWhitespaceDoesNotSend() throws Exception {
        java.util.List<com.cst3492.assistant.chat.ChatRequest> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        Fixture fixture = create(request -> {
            requests.add(request);
            return new MockChatService().reply(request);
        });
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            JTextArea input = (JTextArea) fixture.panel.getInput();
            Object enter = input.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
            Object shiftEnter = input.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,
                            java.awt.event.InputEvent.SHIFT_DOWN_MASK));
            assertNotEquals(enter, shiftEnter);
            input.setText(" \t\n");
            input.getActionMap().get(enter).actionPerformed(new ActionEvent(input, 0, "send"));
            assertNull(field(fixture.panel, "worker", ChatRequestWorker.class));
            assertTrue(field(fixture.panel, "conversation", ConversationView.class).isEmptyStateVisible());
            assertTrue(field(fixture.panel, "status", WrappingLabel.class).getText().contains("Please enter"));
            assertEquals(0, requests.size());

            input.setText("firstsecond");
            input.setCaretPosition(5);
            input.getActionMap().get(shiftEnter).actionPerformed(new ActionEvent(input, 0, "newline"));
            assertEquals("first\nsecond", input.getText());
            assertEquals(0, requests.size());
            input.getActionMap().get(enter).actionPerformed(new ActionEvent(input, 0, "send"));
            ChatRequestWorker worker = field(fixture.panel, "worker", ChatRequestWorker.class);
            assertNotNull(worker);
            worker.addPropertyChangeListener(event -> {
                if (event.getNewValue() == SwingWorker.StateValue.DONE) { done.countDown(); }
            });
            assertEquals("", input.getText());
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals("first\nsecond", requests.get(0).question());
        SwingUtilities.invokeAndWait(() -> {
            assertIdle(fixture);
            fixture.panel.dispose();
        });
    }

    @Test
    void unifiedComposerHasCompactIconOnlySendAndNoShortcutOrFakeUploadControls() throws Exception {
        Fixture fixture = create(new MockChatService());
        SwingUtilities.invokeAndWait(() -> {
            ChatComposer composer = field(fixture.panel, "unifiedComposer", ChatComposer.class);
            JButton send = field(fixture.panel, "send", JButton.class);
            assertEquals("", send.getText());
            assertNotNull(send.getIcon());
            assertNotNull(send.getDisabledIcon());
            assertEquals("Send question", send.getAccessibleContext().getAccessibleName());
            assertTrue(SwingUtilities.isDescendingFrom(fixture.panel.getInput(), composer));
            assertTrue(SwingUtilities.isDescendingFrom(send, composer));
            assertNull(composer.inputScroll().getBorder());
            assertFalse(field(composer, "attachmentActions", javax.swing.JPanel.class).isVisible());
            assertEquals(0, field(composer, "attachmentActions", javax.swing.JPanel.class).getComponentCount());
            for (java.awt.Component component : descendants(fixture.panel)) {
                if (component instanceof javax.swing.JLabel label && label.getText() != null) {
                    assertFalse(label.getText().contains("Enter"), label.getText());
                }
            }
            for (int width : new int[]{240, 340, 760}) {
                fixture.panel.setSize(width, 800);
                for (int pass = 0; pass < 8; pass++) { layoutTree(fixture.panel); }
                assertEquals(com.intellij.util.ui.JBUI.scale(38), send.getWidth());
                assertEquals(send.getWidth(), send.getHeight());
                java.awt.Rectangle bounds = SwingUtilities.convertRectangle(send.getParent(), send.getBounds(), composer);
                assertTrue(bounds.x >= 0 && bounds.x + bounds.width <= composer.getWidth());
                assertTrue(bounds.y >= 0 && bounds.y + bounds.height <= composer.getHeight());
            }
            fixture.panel.dispose();
        });
    }

    @Test
    void inputGrowsToBoundedHeightThenScrollsAndShrinksWhenCleared() throws Exception {
        Fixture fixture = create(new MockChatService());
        SwingUtilities.invokeAndWait(() -> {
            ChatComposer composer = field(fixture.panel, "unifiedComposer", ChatComposer.class);
            JTextArea input = (JTextArea) fixture.panel.getInput();
            fixture.panel.setSize(340, 800);
            for (int pass = 0; pass < 8; pass++) { layoutTree(fixture.panel); }
            int emptyHeight = composer.inputScroll().getPreferredSize().height;
            input.setText("one\ntwo\nthree\nfour");
            for (int pass = 0; pass < 8; pass++) { layoutTree(fixture.panel); }
            int mediumHeight = composer.inputScroll().getPreferredSize().height;
            assertTrue(mediumHeight > emptyHeight);
            input.setText("long line ".repeat(50) + "\nmore lines\n".repeat(100));
            for (int pass = 0; pass < 8; pass++) { layoutTree(fixture.panel); }
            int maximumHeight = composer.inputScroll().getPreferredSize().height;
            assertTrue(maximumHeight > mediumHeight);
            assertTrue(maximumHeight <= input.getFontMetrics(input.getFont()).getHeight() * 7 + 12);
            assertTrue(composer.inputScroll().getVerticalScrollBar().isVisible());
            assertFalse(composer.inputScroll().getHorizontalScrollBar().isVisible());
            input.append("\nstill more".repeat(100));
            for (int pass = 0; pass < 8; pass++) { layoutTree(fixture.panel); }
            assertEquals(maximumHeight, composer.inputScroll().getPreferredSize().height);
            input.setText("");
            for (int pass = 0; pass < 8; pass++) { layoutTree(fixture.panel); }
            assertEquals(emptyHeight, composer.inputScroll().getPreferredSize().height);
            fixture.panel.dispose();
        });
    }

    private static java.util.List<java.awt.Component> descendants(java.awt.Container root) {
        java.util.List<java.awt.Component> result = new java.util.ArrayList<>();
        for (java.awt.Component component : root.getComponents()) {
            result.add(component);
            if (component instanceof java.awt.Container container) { result.addAll(descendants(container)); }
        }
        return result;
    }
}
