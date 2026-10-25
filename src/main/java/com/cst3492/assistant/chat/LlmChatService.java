package com.cst3492.assistant.chat;

import com.cst3492.ta.core.AssistantAnswer;
import com.cst3492.ta.core.AssistantQuery;
import com.cst3492.ta.core.Citation;
import com.cst3492.ta.core.RetrievalResult;
import com.cst3492.ta.llm.AnswerGenerationService;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.concurrent.CancellationException;

/** Query-time retrieval and UI adapter for B's existing service. Call off the EDT. */
public final class LlmChatService implements ChatService {
    private static final Pattern PAGE = Pattern.compile("(?i)(?:p\\.?|page|pages)\\s*([1-9]\\d*)");
    private static final Pattern SLIDE = Pattern.compile("(?i)slides?\\s*([1-9]\\d*)");
    private final AnswerGenerationService generation;
    private final Function<String, RetrievalResult> retrieve;

    public LlmChatService(AnswerGenerationService generation) {
        this(generation, RetrievalResult.empty());
    }

    /** Boundary for a future caller supplying A's genuine retrieval output for this query. */
    public LlmChatService(AnswerGenerationService generation, RetrievalResult retrieval) {
        this(generation, fixedSnapshot(retrieval));
    }

    private static Function<String, RetrievalResult> fixedSnapshot(RetrievalResult retrieval) {
        RetrievalResult snapshot = new RetrievalResult(List.copyOf(Objects.requireNonNull(retrieval).getChunks()));
        return question -> snapshot;
    }

    /** Query-time boundary: production supplies the project's shared knowledge base. */
    public LlmChatService(AnswerGenerationService generation, Function<String, RetrievalResult> retrieve) {
        this.generation = Objects.requireNonNull(generation);
        this.retrieve = Objects.requireNonNull(retrieve);
    }

    @Override
    public ChatReply reply(ChatRequest request) {
        checkCancelled();
        RetrievalResult retrieval;
        try {
            // B's default prompt sends at most five chunks. Validate exactly that same pool.
            retrieval = new RetrievalResult(List.copyOf(Objects.requireNonNull(
                    retrieve.apply(request.question())).getChunks().stream().limit(5).toList()));
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetrievalFailureException(exception);
        }
        checkCancelled();
        AssistantAnswer answer = generation.generate(new AssistantQuery(
                request.question(), request.editorContext(), retrieval));
        // A general-knowledge answer is still a successful answer, not NO_SOURCES or ERROR.
        List<CourseSource> sources = answer.isBasedOnCourseMaterial()
                ? answer.getCitations().stream().filter(citation -> matchesRetrieval(citation, retrieval))
                .map(citation -> source(citation, retrieval)).filter(Objects::nonNull).distinct().toList()
                : List.of();
        return new ChatReply(answer.getContent(), ChatReply.Status.NORMAL, sources);
    }

    private static boolean matchesRetrieval(Citation citation, RetrievalResult retrieval) {
        return retrieval.getChunks().stream().anyMatch(chunk ->
                chunk.getDocumentName().strip().equals(citation.getDocumentName().strip())
                        && Objects.equals(trim(chunk.getPageRange()), trim(citation.getPageRange())));
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    public static final class RetrievalFailureException extends RuntimeException {
        public RetrievalFailureException(Throwable cause) { super("Course retrieval failed", cause); }
    }

    private static String trim(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static CourseSource source(Citation citation, RetrievalResult retrieval) {
        String document = citation.getDocumentName().strip();
        String name = document.toLowerCase(Locale.ROOT);
        CourseSource.Type type;
        Pattern location;
        if (name.endsWith(".pdf")) {
            type = CourseSource.Type.PDF_PAGE;
            location = PAGE;
        } else if (name.endsWith(".ppt") || name.endsWith(".pptx")) {
            type = CourseSource.Type.PPT_SLIDE;
            location = SLIDE;
        } else {
            return null;
        }
        Matcher matcher = location.matcher(Objects.requireNonNullElse(citation.getPageRange(), "").strip());
        if (!matcher.matches()) {
            // Do not silently turn a page range, unknown position, or PDF-style PPT page into one slide.
            return null;
        }
        try {
            List<CourseSource.Excerpt> excerpts = retrieval.getChunks().stream()
                    .filter(chunk -> chunk.getDocumentName().strip().equals(document)
                            && Objects.equals(trim(chunk.getPageRange()), trim(citation.getPageRange())))
                    .map(chunk -> new CourseSource.Excerpt(chunk.getContent(), chunk.getScore()))
                    .toList();
            return new CourseSource(document, type, Integer.parseInt(matcher.group(1)), false, excerpts);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
