package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatReply;
import com.cst3492.assistant.chat.ChatRequest;
import com.cst3492.assistant.chat.ChatService;
import com.cst3492.assistant.chat.MockChatService;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestWorkerTest {
    private final ChatRequest request = new ChatRequest("Question", "");

    @Test
    void serviceRunsOffEdtAndCompletionAndRestoreRunOnEdt() throws Exception {
        AtomicBoolean background = new AtomicBoolean();
        AtomicBoolean completionEdt = new AtomicBoolean();
        AtomicBoolean restoreEdt = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> new ChatRequestWorker(req -> {
            background.set(!SwingUtilities.isEventDispatchThread());
            return new MockChatService().reply(req);
        }, request, pending -> true, reply -> completionEdt.set(SwingUtilities.isEventDispatchThread()),
                () -> fail("Not cancelled"), pending -> {
                    restoreEdt.set(SwingUtilities.isEventDispatchThread());
                    finished.countDown();
                }).execute());
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(background.get());
        assertTrue(completionEdt.get());
        assertTrue(restoreEdt.get());
    }

    @Test
    void exceptionProducesSafeErrorAndAlwaysRestoresSendingState() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean sending = new AtomicBoolean(true);
        AtomicReference<ChatReply> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> new ChatRequestWorker(req -> {
            throw new IllegalStateException("private endpoint body must not reach UI");
        }, request, pending -> true, result::set, () -> fail("Not cancelled"), pending -> {
            sending.set(false);
            finished.countDown();
        }).execute());
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(sending.get());
        assertEquals(ChatReply.Status.ERROR, result.get().status());
        assertFalse(result.get().text().contains("private endpoint body"));
    }

    @Test
    void cancellationRestoresStateOnEdt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        ChatRequestWorker worker = new ChatRequestWorker(blockingService(entered), request, pending -> true,
                reply -> fail("Cancelled request must not append a reply"),
                () -> cancelled.set(SwingUtilities.isEventDispatchThread()), pending -> finished.countDown());
        SwingUtilities.invokeAndWait(worker::execute);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> worker.cancel(true));
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(cancelled.get());
    }

    @Test
    void closedProjectSuppressesPresentationButStillCleansRequestState() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean callback = new AtomicBoolean();
        AtomicBoolean cleaned = new AtomicBoolean();
        ChatRequestWorker worker = new ChatRequestWorker(blockingService(entered), request, pending -> active.get(),
                reply -> callback.set(true), () -> callback.set(true), pending -> cleaned.set(true));
        worker.addPropertyChangeListener(event -> {
            if (event.getNewValue() == SwingWorker.StateValue.DONE) {
                done.countDown();
            }
        });
        SwingUtilities.invokeAndWait(worker::execute);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            active.set(false);
            worker.cancel(true);
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertFalse(callback.get());
        assertTrue(cleaned.get());
    }

    @Test
    void wrappedBackgroundInterruptionIsCancellationNotServiceFailure() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean replied = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> new ChatRequestWorker(req -> {
            throw new IllegalStateException("wrapped interruption", new InterruptedException());
        }, request, pending -> true, reply -> replied.set(true), () -> cancelled.set(true),
                pending -> finished.countDown()).execute());
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(cancelled.get());
        assertFalse(replied.get());
    }

    @Test
    void completionCanDeactivateOwnerBeforeCleanupWithoutUiRestoration() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean restoredUi = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> new ChatRequestWorker(new MockChatService(), request,
                pending -> active.get(), reply -> active.set(false), () -> fail("Not cancelled"), pending -> {
                    if (active.get()) {
                        restoredUi.set(true);
                    }
                    finished.countDown();
                }).execute());
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(restoredUi.get());
    }

    @Test
    void cancelledServiceIgnoringInterruptCannotDeliverLateReplyOrRepeatCleanup() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger replies = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger cleanups = new java.util.concurrent.atomic.AtomicInteger();
        ChatRequestWorker worker = new ChatRequestWorker(req -> {
            entered.countDown();
            boolean released = false;
            while (!released) {
                try {
                    released = release.await(5, TimeUnit.SECONDS);
                    if (!released) {
                        throw new IllegalStateException("Test service timed out");
                    }
                } catch (InterruptedException ignored) {
                    // Deliberately non-cooperative service to exercise best-effort cancellation.
                }
            }
            returned.countDown();
            return new MockChatService().reply(req);
        }, request, pending -> true, reply -> replies.incrementAndGet(), () -> { }, pending -> {
            cleanups.incrementAndGet();
            finished.countDown();
        });
        try {
            SwingUtilities.invokeAndWait(worker::execute);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> worker.cancel(true));
            assertTrue(worker.isCancelled());
            assertEquals(1, returned.getCount()); // Future cancelled, actual service still running.
        } finally {
            release.countDown();
        }
        assertTrue(returned.await(5, TimeUnit.SECONDS));
        // JDK 25 can defer SwingWorker.done() until doInBackground actually exits.
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(0, replies.get());
        assertEquals(1, cleanups.get());
    }

    private ChatService blockingService(CountDownLatch entered) {
        return req -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return new MockChatService().reply(req);
        };
    }

    @Test
    void nullServiceResultBecomesErrorAndStillFinishes() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<ChatReply> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> new ChatRequestWorker(req -> null, request, pending -> true,
                result::set, () -> fail("Not cancelled"), pending -> finished.countDown()).execute());
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals(ChatReply.Status.ERROR, result.get().status());
    }
}
