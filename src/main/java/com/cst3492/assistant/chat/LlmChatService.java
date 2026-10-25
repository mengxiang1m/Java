package com.cst3492.assistant.chat;

import com.cst3492.ta.core.AssistantAnswer;
import com.cst3492.ta.core.AssistantQuery;
import com.cst3492.ta.core.Citation;
import com.cst3492.ta.core.RetrievalResult;
import com.cst3492.ta.llm.AnswerGenerationService;
import com.cst3492.ta.rag.KnowledgeBaseService;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Retrieves for each question, calls B, and maps verified citations. Call off the EDT. */
public final class LlmChatService implements ChatService {
    private static final Pattern PAGE = Pattern.compile("(?i)(?:p\\.?|page|pages)\\s*([1-9]\\d*)");
    private static final Pattern SLIDE = Pattern.compile("(?i)slides?\\s*([1-9]\\d*)");
    private final AnswerGenerationService generation;
    private final Function<String, RetrievalResult> retrieve;

    public LlmChatService(AnswerGenerationService generation) {
        this(generation, RetrievalResult.empty());
    }

    /** Compatibility constructor for callers supplying a fixed single-query snapshot. */
    public LlmChatService(AnswerGenerationService generation, RetrievalResult retrieval) {
        this.generation = Objects.requireNonNull(generation);
        RetrievalResult snapshot = new RetrievalResult(List.copyOf(Objects.requireNonNull(retrieval).getChunks()));
        this.retrieve = question -> snapshot;
    }

    /** Import and chat must share this knowledge base for the lifetime of the Tool Window. */
    public LlmChatService(AnswerGenerationService generation, KnowledgeBaseService knowledge) {
        this.generation = Objects.requireNonNull(generation);
        this.retrieve = Objects.requireNonNull(knowledge)::retrieve;
    }

    @Override
    public ChatReply reply(ChatRequest request) {
        RetrievalResult retrieval = retrieve.apply(request.question());
        AssistantAnswer answer = generation.generate(new AssistantQuery(
                request.question(), request.editorContext(), retrieval));
        // A general-knowledge answer is still a successful answer, not NO_SOURCES or ERROR.
        List<CourseSource> sources = answer.isBasedOnCourseMaterial()
                ? answer.getCitations().stream().filter(citation -> matchesRetrieval(citation, retrieval))
                .map(LlmChatService::source).filter(Objects::nonNull).distinct().toList()
                : List.of();
        return new ChatReply(answer.getContent(), ChatReply.Status.NORMAL, sources);
    }

    private static boolean matchesRetrieval(Citation citation, RetrievalResult retrieval) {
        return retrieval.getChunks().stream().anyMatch(chunk ->
                chunk.getDocumentName().strip().equals(citation.getDocumentName().strip())
                        && Objects.equals(trim(chunk.getPageRange()), trim(citation.getPageRange())));
    }

    private static String trim(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static CourseSource source(Citation citation) {
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
            return new CourseSource(document, type, Integer.parseInt(matcher.group(1)), false);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
