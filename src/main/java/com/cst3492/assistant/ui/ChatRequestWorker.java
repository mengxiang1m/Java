package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatReply;
import com.cst3492.assistant.chat.ChatRequest;
import com.cst3492.assistant.chat.ChatService;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.SwingWorker;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** One request on Swing's shared worker pool; completion and callbacks run on the EDT. */
final class ChatRequestWorker extends SwingWorker<ChatReply, Void> {
    private static final Logger LOG = Logger.getInstance(ChatRequestWorker.class);
    private final ChatService service;
    private final ChatRequest request;
    private final Predicate<ChatRequestWorker> active;
    private final Consumer<ChatReply> completed;
    private final Runnable cancelled;
    private final Consumer<ChatRequestWorker> finished;

    ChatRequestWorker(ChatService service, ChatRequest request, Predicate<ChatRequestWorker> active,
                      Consumer<ChatReply> completed, Runnable cancelled, Consumer<ChatRequestWorker> finished) {
        this.service = service;
        this.request = request;
        this.active = active;
        this.completed = completed;
        this.cancelled = cancelled;
        this.finished = finished;
    }

    @Override
    protected ChatReply doInBackground() {
        return Objects.requireNonNull(service.reply(request), "Chat service returned no reply");
    }

    @Override
    protected void done() {
        try {
            if (!active.test(this)) {
                return; // Skip presentation, but still release request state in finally.
            }
            ChatReply reply;
            try {
                reply = get();
            } catch (CancellationException exception) {
                cancelled.run();
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                cancelled.run();
                return;
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (isInterrupted(cause)) {
                    cancelled.run();
                    return;
                }
                // Endpoint errors can contain response bodies; never expose/log raw exception text or keys.
                LOG.warn("Chat request failed; exception type: " + cause.getClass().getName()
                        + ". Response details omitted.");
                String message = cause instanceof com.cst3492.assistant.chat.LlmChatService.RetrievalFailureException
                        ? "Course retrieval failed. Check the knowledge base and try again."
                        : "The service could not reply. Check API configuration and network, then try again.";
                reply = new ChatReply(message,
                        ChatReply.Status.ERROR, List.of());
            }
            completed.accept(reply);
        } finally {
            // Always run bookkeeping, including inactive/closed cases. This callback must
            // verify ownership and liveness itself before touching any Swing component.
            finished.accept(this);
        }
    }

    private static boolean isInterrupted(Throwable failure) {
        // B's HTTP client can wrap InterruptedException in LlmException.
        for (int depth = 0; failure != null && depth < 32; depth++, failure = failure.getCause()) {
            if (failure instanceof InterruptedException || failure instanceof CancellationException) {
                return true;
            }
        }
        return false;
    }
}
