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

/** UI adapter for B's existing service. No retrieval is performed here. Call off the EDT. */
public final class LlmChatService implements ChatService {
    private static final Pattern PAGE = Pattern.compile("(?i)(?:p\\.?|page|pages)\\s*([1-9]\\d*)");
    private static final Pattern SLIDE = Pattern.compile("(?i)slides?\\s*([1-9]\\d*)");
    private final AnswerGenerationService generation;
    private final RetrievalResult retrieval;

    public LlmChatService(AnswerGenerationService generation) {
        this(generation, RetrievalResult.empty());
    }

    /** Boundary for a future caller supplying A's genuine retrieval output for this query. */
    public LlmChatService(AnswerGenerationService generation, RetrievalResult retrieval) {
        this.generation = Objects.requireNonNull(generation);
        this.retrieval = new RetrievalResult(List.copyOf(Objects.requireNonNull(retrieval).getChunks()));
    }

    @Override
    public ChatReply reply(ChatRequest request) {
        AssistantAnswer answer = generation.generate(new AssistantQuery(
                request.question(), request.editorContext(), retrieval));
        // A general-knowledge answer is still a successful answer, not NO_SOURCES or ERROR.
        List<CourseSource> sources = answer.isBasedOnCourseMaterial()
                ? answer.getCitations().stream().filter(this::matchesRetrieval)
                .map(LlmChatService::source).filter(Objects::nonNull).distinct().toList()
                : List.of();
        return new ChatReply(answer.getContent(), ChatReply.Status.NORMAL, sources);
    }

    private boolean matchesRetrieval(Citation citation) {
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
