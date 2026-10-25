package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatReply;
import com.cst3492.assistant.chat.ChatService;
import com.cst3492.assistant.chat.LlmChatService;
import com.cst3492.assistant.chat.MockChatService;
import com.cst3492.ta.llm.AnswerGenerationService;
import com.cst3492.ta.llm.LlmConfig;
import com.cst3492.ta.rag.KnowledgeBaseService;
import com.google.gson.Gson;
import com.intellij.openapi.project.Project;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CourseImportPanelTest {
    @TempDir Path temp;

    @org.junit.jupiter.api.BeforeAll
    static void enableIcons() { com.intellij.openapi.util.IconLoader.activate(); }

    @org.junit.jupiter.api.AfterAll
    static void resetIcons() { com.intellij.openapi.util.IconLoader.deactivate(); }

    @Test
    void importedDocumentReachesPromptAndActualAnswerCardWithItsPhysicalSlide() throws Exception {
        Path document = temp.resolve("Lecture.pptx");
        try (var slides = new XMLSlideShow(); var output = Files.newOutputStream(document)) {
            slides.createSlide();
            slides.createSlide().createTextBox().setText("JDBC transaction rollback");
            slides.write(output);
        }
        var knowledge = new KnowledgeBaseService();
        var prompt = new AtomicReference<String>();
        String source = document.toRealPath().toString();
        var generation = new AnswerGenerationService(LlmConfig.deepseek("unused-test-key"), (config, messages) -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            prompt.set(messages.getUser());
            return new Gson().toJson(Map.of("answer", "Use rollback [1]", "basedOnCourseMaterial", true,
                    "citations", List.of(Map.of("document", source, "pageRange", "slide 2"))));
        });
        AssistantChatPanel panel = panel(new LlmChatService(generation, knowledge), knowledge);
        CountDownLatch imported = new CountDownLatch(1);
        CountDownLatch answered = new CountDownLatch(1);
        String code = "\tconnection.rollback();\r\n";
        SwingUtilities.invokeAndWait(() -> {
            JTextArea input = (JTextArea) panel.getInput();
            input.setText("JDBC");
            panel.setEditorContext(code);
            onEnabled(importButton(panel), imported);
            panel.importDocuments(List.of(document));
            assertFalse(importButton(panel).isEnabled());
            assertFalse(sendButton(panel).isEnabled());
            assertTrue(input.isEditable());
            assertEquals("JDBC", input.getText());
        });
        assertTrue(imported.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("JDBC", ((JTextArea) panel.getInput()).getText());
            onEnabled(sendButton(panel), answered);
            panel.getInput().getActionMap().get("sendQuestion")
                    .actionPerformed(new ActionEvent(panel, 0, "send"));
            assertFalse(importButton(panel).isEnabled());
        });
        assertTrue(answered.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            ChatReply reply = descendants(panel).stream().filter(MessageCard.class::isInstance)
                    .map(MessageCard.class::cast).map(MessageCard::reply)
                    .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            assertEquals("Use rollback [1]", reply.text());
            assertEquals(1, reply.sources().size());
            assertEquals(source, reply.sources().getFirst().documentName());
            assertEquals(2, reply.sources().getFirst().number());
            assertFalse(reply.sources().getFirst().testData());
            assertTrue(importButton(panel).isEnabled());
            panel.dispose();
        });
        assertTrue(prompt.get().contains("JDBC transaction rollback"));
        assertTrue(prompt.get().contains(code));
    }

    @Test
    void failedImportPreservesTheQuestionDraftAndRestoresBothControls() throws Exception {
        AssistantChatPanel panel = panel(new MockChatService(), new KnowledgeBaseService());
        CountDownLatch imported = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            ((JTextArea) panel.getInput()).setText("Keep this draft");
            onEnabled(importButton(panel), imported);
            panel.importDocuments(List.of(temp.resolve("missing.pdf")));
        });
        assertTrue(imported.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("Keep this draft", ((JTextArea) panel.getInput()).getText());
            assertTrue(importButton(panel).isEnabled());
            assertTrue(sendButton(panel).isEnabled());
            JTextArea details = descendants(panel).stream().filter(JTextArea.class::isInstance)
                    .map(JTextArea.class::cast)
                    .filter(area -> "Course material import details".equals(area.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertTrue(details.getText().contains("missing.pdf"));
            assertFalse(details.getText().contains("Exception"));
            panel.dispose();
        });
    }

    @Test
    void failedAnswerRestoresTheImportButton() throws Exception {
        AssistantChatPanel panel = panel(request -> { throw new IllegalStateException("Test failure"); },
                new KnowledgeBaseService());
        CountDownLatch answered = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            ((JTextArea) panel.getInput()).setText("JDBC");
            onEnabled(importButton(panel), answered);
            panel.getInput().getActionMap().get("sendQuestion")
                    .actionPerformed(new ActionEvent(panel, 0, "send"));
            assertFalse(importButton(panel).isEnabled());
        });
        assertTrue(answered.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(importButton(panel).isEnabled());
            assertTrue(sendButton(panel).isEnabled());
            panel.dispose();
        });
    }

    private AssistantChatPanel panel(ChatService service, KnowledgeBaseService knowledge) throws Exception {
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isDisposed" -> false;
                    case "getName", "toString" -> "Import test project";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var panel = new AtomicReference<AssistantChatPanel>();
        SwingUtilities.invokeAndWait(() -> panel.set(new AssistantChatPanel(project, service, "Test mode", knowledge)));
        return panel.get();
    }

    private static void onEnabled(JButton button, CountDownLatch latch) {
        button.addPropertyChangeListener("enabled", event -> {
            if (Boolean.TRUE.equals(event.getNewValue())) latch.countDown();
        });
    }

    private static JButton importButton(Container panel) {
        return descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "Import materials".equals(button.getText())).findFirst().orElseThrow();
    }

    private static JButton sendButton(Container panel) {
        return descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "Send question".equals(button.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static List<Component> descendants(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            result.add(child);
            if (child instanceof Container container) result.addAll(descendants(container));
        }
        return result;
    }
}
