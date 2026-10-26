package com.cst3492.assistant.ui;

import com.cst3492.assistant.knowledge.ProjectKnowledgeBase;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBasePanelTest {
    private KnowledgeBasePanel create(ProjectKnowledgeBase knowledge) throws Exception {
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
                new Class<?>[]{Project.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isDisposed" -> false;
                    case "toString", "getName" -> "Library test project";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var panel = new AtomicReference<KnowledgeBasePanel>();
        SwingUtilities.invokeAndWait(() -> panel.set(new KnowledgeBasePanel(project, knowledge)));
        return panel.get();
    }

    private Object field(KnowledgeBasePanel panel, String name) {
        try {
            var field = KnowledgeBasePanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(panel);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    @Test void libraryFailureRestoresActionsWithoutExposingRawErrors() throws Exception {
        try (var knowledge = new ProjectKnowledgeBase()) {
            var panel = create(knowledge);
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean background = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> {
                panel.start(() -> {
                    background.set(!SwingUtilities.isEventDispatchThread());
                    throw new IllegalStateException("private file/parser details");
                });
                ((SwingWorker<?, ?>) field(panel, "worker")).addPropertyChangeListener(event -> {
                    if (event.getNewValue() == SwingWorker.StateValue.DONE) done.countDown();
                });
                assertFalse(((JButton) field(panel, "importButton")).isEnabled());
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(background.get());
                assertNull(field(panel, "worker"));
                assertTrue(((JButton) field(panel, "importButton")).isEnabled());
                assertFalse(((WrappingLabel) field(panel, "feedback")).getText().contains("private"));
                panel.dispose();
            });
        }
    }

    @Test void disposedLibrarySuppressesLateCompletionAndDuplicateOperations() throws Exception {
        try (var knowledge = new ProjectKnowledgeBase()) {
            var panel = create(knowledge);
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean duplicate = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> {
                panel.start(() -> {
                    entered.countDown();
                    try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                    return "Late result must not appear";
                });
                ((SwingWorker<?, ?>) field(panel, "worker")).addPropertyChangeListener(event -> {
                    if (event.getNewValue() == SwingWorker.StateValue.DONE) done.countDown();
                });
                panel.start(() -> { duplicate.set(true); return "duplicate"; });
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(panel::dispose);
            release.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(duplicate.get());
                assertNull(field(panel, "worker"));
                assertFalse(((JButton) field(panel, "importButton")).isEnabled());
                assertFalse(((WrappingLabel) field(panel, "feedback")).getText().contains("Late result"));
            });
        }
    }
}
