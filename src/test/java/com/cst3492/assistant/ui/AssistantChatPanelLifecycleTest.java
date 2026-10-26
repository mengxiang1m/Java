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
    @Test
    void llmFailureRestoresComposerAndNextQuestionSucceeds() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service = new com.cst3492.assistant.chat.LlmChatService(
                new com.cst3492.ta.llm.AnswerGenerationService(
                        com.cst3492.ta.llm.LlmConfig.deepseek("offline-test-placeholder"), (config, prompt) -> {
                    if (calls.getAndIncrement() == 0) throw new com.cst3492.ta.llm.LlmException("private API details");
                    return "Recovered answer";
                }), question -> com.cst3492.ta.core.RetrievalResult.empty());
        Fixture fixture = create(service);
        for (int i = 0; i < 2; i++) {
            CountDownLatch done = new CountDownLatch(1);
            start(fixture, done);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> assertIdle(fixture));
        }
        SwingUtilities.invokeAndWait(() -> {
            var replies = field(fixture.panel, "conversation", ConversationView.class).messages().stream()
                    .filter(card -> card.reply() != null).map(MessageCard::reply).toList();
            assertEquals(com.cst3492.assistant.chat.ChatReply.Status.ERROR, replies.get(0).status());
            assertEquals(com.cst3492.assistant.chat.ChatReply.Status.NORMAL, replies.get(1).status());
            assertTrue(replies.get(1).text().startsWith("Recovered answer"));
            fixture.panel.dispose();
        });
    }

    @org.junit.jupiter.api.BeforeAll
    static void enablePlatformIconRendering() {
        com.intellij.openapi.util.IconLoader.activate();
    }

    @org.junit.jupiter.api.AfterAll
    static void resetPlatformIconRendering() {
        com.intellij.openapi.util.IconLoader.deactivate();
    }

    private Fixture create(ChatService service) throws Exception {
        return create(service, null);
    }

    private Fixture create(ChatService service, com.cst3492.assistant.knowledge.ProjectKnowledgeBase knowledge) throws Exception {
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
        SwingUtilities.invokeAndWait(() -> panel.set(new AssistantChatPanel(project, service, "Test mode", knowledge)));
        return new Fixture(panel.get(), closed);
    }

    private record Fixture(AssistantChatPanel panel, AtomicBoolean closed) { }

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path attachmentDirectory;

    private java.nio.file.Path pdf(String name) throws Exception {
        return pdf(name, "Question JDBC original course evidence");
    }

    private java.nio.file.Path pdf(String name, String text) throws Exception {
        var path = attachmentDirectory.resolve(name);
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(40, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(path.toFile());
        }
        return path;
    }

    private java.util.List<java.awt.Component> children(java.awt.Container container) {
        var result = new java.util.ArrayList<java.awt.Component>();
        for (var component : container.getComponents()) {
            result.add(component);
            if (component instanceof java.awt.Container nested) result.addAll(children(nested));
        }
        return result;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"pdf", "pptx", "ppt"})
    void attachmentToHttpToRenderedAnswerUsesTheSameProjectKnowledge(String format) throws Exception {
        var path = attachmentDirectory.resolve("http-course." + format);
        if (format.equals("pdf")) {
            path = pdf("http-course.pdf");
        } else if (format.equals("pptx")) {
            try (var slides = new org.apache.poi.xslf.usermodel.XMLSlideShow();
                 var output = java.nio.file.Files.newOutputStream(path)) {
                slides.createSlide().createTextBox().setText("Question JDBC original course evidence");
                slides.write(output);
            }
        } else {
            try (var slides = new org.apache.poi.hslf.usermodel.HSLFSlideShow();
                 var output = java.nio.file.Files.newOutputStream(path)) {
                slides.createSlide().createTextBox().setText("Question JDBC original course evidence");
                slides.write(output);
            }
        }
        var submittedFile = path;
        var captured = new AtomicReference<String>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            String answer = "{\"answer\":\"Use the course JDBC guidance [1].\","
                    + "\"citations\":[{\"sourceId\":1}],\"basedOnCourseMaterial\":true}";
            byte[] body = new com.google.gson.Gson().toJson(java.util.Map.of("choices", java.util.List.of(
                    java.util.Map.of("message", java.util.Map.of("content", answer), "finish_reason", "stop"))))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            exchange.close();
        });
        server.start();
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            var selection = com.cst3492.assistant.chat.ChatServiceFactory.create(java.util.Map.of(
                    "CST3492_LLM_PROVIDER", "custom", "CST3492_LLM_API_KEY", "offline-placeholder",
                    "CST3492_LLM_MODEL", "local-fixture", "CST3492_LLM_BASE_URL",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), q -> knowledge.retrieve(q, 5));
            Fixture fixture = create(selection.service(), knowledge);
            try {
                String code = "\tconnection.rollback();\r\n";
                SwingUtilities.invokeAndWait(() -> {
                    fixture.panel.setEditorContext(code);
                    fixture.panel.setAttachments(java.util.List.of(submittedFile));
                });
                var done = new CountDownLatch(1);
                start(fixture, done);
                assertTrue(done.await(10, TimeUnit.SECONDS));
                var user = com.google.gson.JsonParser.parseString(captured.get()).getAsJsonObject()
                        .getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString();
                assertTrue(user.contains("JDBC original course evidence"));
                assertTrue(user.contains(code));
                assertTrue(user.contains(submittedFile.toRealPath().toString()));
                SwingUtilities.invokeAndWait(() -> {
                    assertIdle(fixture);
                    var card = field(fixture.panel, "conversation", ConversationView.class).messages().stream()
                            .filter(item -> item.reply() != null).findFirst().orElseThrow();
                    assertEquals("Use the course JDBC guidance [1].", card.reply().text());
                    assertEquals(com.cst3492.assistant.chat.ChatReply.Status.NORMAL, card.reply().status());
                    assertEquals(1, card.reply().sources().size());
                    var source = card.reply().sources().getFirst();
                    assertEquals(1, source.number());
                    assertEquals(format.equals("pdf") ? com.cst3492.assistant.chat.CourseSource.Type.PDF_PAGE
                            : com.cst3492.assistant.chat.CourseSource.Type.PPT_SLIDE, source.type());
                    assertFalse(source.testData());
                    assertFalse(source.excerpts().isEmpty());
                    card.sources().setExpanded(true);
                    assertEquals(1, card.sources().cards().size());
                });
            } finally {
                SwingUtilities.invokeAndWait(fixture.panel::dispose);
            }
        } finally {
            server.stop(0);
        }
    }

    @Test void selectedThenRemovedAttachmentIsNeverImported() throws Exception {
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            Fixture fixture = create(new MockChatService(), knowledge);
            var file = pdf("removed.pdf");
            SwingUtilities.invokeAndWait(() -> {
                fixture.panel.setAttachments(java.util.List.of(file));
                assertTrue(knowledge.snapshot().documents().isEmpty());
                children(fixture.panel).stream().filter(c -> c instanceof JButton b && "×".equals(b.getText()))
                        .map(c -> (JButton) c).findFirst().orElseThrow().doClick();
            });
            var done = new CountDownLatch(1);
            start(fixture, done);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(knowledge.snapshot().documents().isEmpty());
            SwingUtilities.invokeAndWait(() -> { assertIdle(fixture); fixture.panel.dispose(); });
        }
    }

    @Test void multipleAttachmentsImportBeforeLlmAndSuccessfulCardsClear() throws Exception {
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            var first = pdf("first.pdf");
            var second = pdf("second.pdf");
            AtomicBoolean background = new AtomicBoolean();
            var llm = new com.cst3492.assistant.chat.LlmChatService(new com.cst3492.ta.llm.AnswerGenerationService(
                    com.cst3492.ta.llm.LlmConfig.deepseek("offline-test-placeholder"), (config, prompt) -> {
                background.set(!SwingUtilities.isEventDispatchThread());
                assertEquals(2, knowledge.snapshot().documents().size());
                assertTrue(prompt.getUser().contains("JDBC original course evidence"));
                return new com.google.gson.Gson().toJson(java.util.Map.of("answer", "Offline grounded answer [1]",
                        "basedOnCourseMaterial", true, "citations", knowledge.retrieve("Question", 5).getChunks()
                                .stream().map(chunk -> java.util.Map.of("document", chunk.getDocumentName(),
                                        "pageRange", chunk.getPageRange())).toList()));
            }), q -> knowledge.retrieve(q, 5));
            Fixture fixture = create(llm, knowledge);
            SwingUtilities.invokeAndWait(() -> fixture.panel.setAttachments(java.util.List.of(first, second, first)));
            var done = new CountDownLatch(1);
            start(fixture, done);
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertTrue(background.get());
            assertEquals(2, knowledge.snapshot().documents().size());
            SwingUtilities.invokeAndWait(() -> {
                assertIdle(fixture);
                assertFalse(children(fixture.panel).stream().anyMatch(c -> c instanceof JButton b && "×".equals(b.getText())));
                var reply = field(fixture.panel, "conversation", ConversationView.class).messages().stream()
                        .filter(card -> card.reply() != null).findFirst().orElseThrow().reply();
                assertEquals(2, reply.sources().size());
                assertTrue(reply.sources().stream().allMatch(source -> !source.testData() && !source.excerpts().isEmpty()));
            });
            var next = new CountDownLatch(1);
            start(fixture, next);
            assertTrue(next.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(fixture.panel::dispose);
        }
    }

    @Test void failedAttachmentStopsAnsweringCanRetryAndKeepsPreviousLibrary() throws Exception {
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            knowledge.ingest(pdf("existing.pdf"));
            var damaged = attachmentDirectory.resolve("retry.pdf");
            java.nio.file.Files.writeString(damaged, "broken PDF");
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            Fixture fixture = create(request -> { calls.incrementAndGet(); return new MockChatService().reply(request); }, knowledge);
            SwingUtilities.invokeAndWait(() -> fixture.panel.setAttachments(java.util.List.of(damaged)));
            var done = new CountDownLatch(1);
            start(fixture, done);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(0, calls.get());
            assertFalse(knowledge.retrieve("JDBC", 5).isEmpty());
            SwingUtilities.invokeAndWait(() -> {
                assertIdle(fixture);
                assertTrue(children(fixture.panel).stream().anyMatch(c -> c instanceof JButton b && "×".equals(b.getText())));
            });
            pdf("retry.pdf");
            var retry = new CountDownLatch(1);
            start(fixture, retry);
            assertTrue(retry.await(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertEquals(2, knowledge.snapshot().documents().size());
            SwingUtilities.invokeAndWait(fixture.panel::dispose);
        }
    }

    @Test void closedProjectWithPendingAttachmentCannotImport() throws Exception {
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            Fixture fixture = create(new MockChatService(), knowledge);
            var path = pdf("pending.pdf");
            SwingUtilities.invokeAndWait(() -> {
                fixture.panel.setAttachments(java.util.List.of(path));
                fixture.closed.set(true);
                var input = (JTextArea) fixture.panel.getInput();
                input.setText("Question");
                input.getActionMap().get("sendQuestion").actionPerformed(new ActionEvent(input, 0, "send"));
                assertNull(field(fixture.panel, "worker", ChatRequestWorker.class));
                fixture.panel.dispose();
            });
            assertTrue(knowledge.snapshot().documents().isEmpty());
        }
    }

    @Test void removingPendingCardDoesNotDeleteAlreadyImportedFile() throws Exception {
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            var path = pdf("already-imported.pdf");
            knowledge.ingest(path);
            Fixture fixture = create(new MockChatService(), knowledge);
            SwingUtilities.invokeAndWait(() -> {
                fixture.panel.setAttachments(java.util.List.of(path));
                children(fixture.panel).stream().filter(c -> c instanceof JButton b && "×".equals(b.getText()))
                        .map(c -> (JButton) c).findFirst().orElseThrow().doClick();
                fixture.panel.dispose();
            });
            assertEquals(1, knowledge.snapshot().documents().size());
            assertFalse(knowledge.retrieve("JDBC", 5).isEmpty());
        }
    }

    @Test void chatContainsNoLegacyManagerOrPopupEntry() throws Exception {
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            Fixture fixture = create(new MockChatService(), knowledge);
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(children(fixture.panel).stream().anyMatch(c -> c instanceof KnowledgeBasePanel
                        || c instanceof javax.swing.JComboBox || c instanceof javax.swing.JPopupMenu));
                var texts = children(fixture.panel).stream().filter(c -> c instanceof JButton)
                        .map(c -> ((JButton) c).getText()).toList();
                assertFalse(texts.contains("Import course file"));
                assertFalse(texts.contains("Remove"));
                assertFalse(texts.contains("⋯"));
                assertTrue(texts.contains("+"));
                fixture.panel.dispose();
            });
        }
    }

    @Test void damagedReattachedFileReportsErrorAndPreservesTheSavedIndex() throws Exception {
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            var path = pdf("cached.pdf");
            knowledge.ingest(path);
            // Explicit reattachment refreshes content; a failed refresh must retain old knowledge.
            java.nio.file.Files.writeString(path, "changed to damaged content");
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            Fixture fixture = create(request -> {
                calls.incrementAndGet();
                assertFalse(knowledge.retrieve("JDBC", 5).isEmpty());
                return new MockChatService().reply(request);
            }, knowledge);
            SwingUtilities.invokeAndWait(() -> fixture.panel.setAttachments(java.util.List.of(path, path)));
            var done = new CountDownLatch(1);
            start(fixture, done);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(0, calls.get());
            assertEquals(1, knowledge.snapshot().documents().size());
            assertFalse(knowledge.retrieve("JDBC", 5).isEmpty());
            SwingUtilities.invokeAndWait(() -> { assertIdle(fixture); fixture.panel.dispose(); });
        }
    }

    @Test void reattachingUpdatedFileRefreshesKnowledgeBeforeAnswering() throws Exception {
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            var path = pdf("refresh.pdf");
            knowledge.ingest(path);
            pdf("refresh.pdf", "JUnit new evidence");
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            Fixture fixture = create(request -> {
                calls.incrementAndGet();
                assertTrue(knowledge.retrieve("JDBC", 5).isEmpty());
                assertFalse(knowledge.retrieve("JUnit", 5).isEmpty());
                return new MockChatService().reply(request);
            }, knowledge);
            SwingUtilities.invokeAndWait(() -> fixture.panel.setAttachments(java.util.List.of(path)));
            var done = new CountDownLatch(1);
            start(fixture, done);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertTrue(knowledge.retrieve("JDBC", 5).isEmpty());
            assertFalse(knowledge.retrieve("JUnit", 5).isEmpty());
            SwingUtilities.invokeAndWait(() -> { assertIdle(fixture); fixture.panel.dispose(); });
        }
    }

    @Test void waitingImportDoesNotBlockEdtAndCancellationPreventsQueuedImport() throws Exception {
        try (var knowledge = new com.cst3492.assistant.knowledge.ProjectKnowledgeBase()) {
            Fixture fixture = create(request -> { fail("Cancelled import must not call service"); return null; }, knowledge);
            var path = pdf("cancelled.pdf");
            SwingUtilities.invokeAndWait(() -> fixture.panel.setAttachments(java.util.List.of(path)));
            var mutations = knowledge.getClass().getDeclaredField("mutations");
            mutations.setAccessible(true);
            CountDownLatch done = new CountDownLatch(1);
            synchronized (mutations.get(knowledge)) {
                ChatRequestWorker worker = start(fixture, done);
                // A queued import is blocked on a background lock; the EDT still processes input.
                SwingUtilities.invokeAndWait(() -> {
                    assertFalse(field(fixture.panel, "send", JButton.class).isEnabled());
                    worker.cancel(true);
                });
            }
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(knowledge.snapshot().documents().isEmpty());
            SwingUtilities.invokeAndWait(() -> { assertIdle(fixture); fixture.panel.dispose(); });
        }
    }

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
