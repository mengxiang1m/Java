package com.cst3492.ta.llm;

import com.cst3492.ta.core.AssistantAnswer;
import com.cst3492.ta.core.Citation;
import com.cst3492.ta.core.RetrievedChunk;
import com.cst3492.ta.core.RetrievalResult;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the LLM's raw text (expected to be the JSON object described in the
 * system prompt) into an {@link AssistantAnswer}.
 *
 * <p>The parser is deliberately tolerant: it strips markdown fences and any
 * leading prose, locates the outermost JSON object, and falls back to a
 * plain-text answer when the model does not return valid JSON.</p>
 *
 * <p>Grounding is strict: an answer is {@code basedOnCourseMaterial} only when
 * the model claims it, the retrieval found relevant material, <em>and</em> at
 * least one returned citation matches a retrieved chunk's document name and
 * page range. Citations the model invents are dropped, so a model can never
 * cite a source (or page) it was not given.</p>
 */
public final class AnswerParser {

    private static final Gson GSON = new Gson();
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final Pattern UNVERIFIED_REFERENCE = Pattern.compile(
            "(?s)(```.*?(?:```|\\z)|`[^`\\r\\n]*`)"
                    + "|[ \\t]*(?<![\\p{L}\\p{N}_\\]])\\[\\d+\\](?!\\()");

    /**
     * Parses the model output, cross-checking any citations against the
     * retrieved chunks.
     *
     * @param rawContent the assistant message content from the LLM
     * @param retrieval  the retrieval result the prompt was grounded in
     */
    public AssistantAnswer parse(String rawContent, RetrievalResult retrieval) {
        Objects.requireNonNull(retrieval, "retrieval");
        if (rawContent == null || rawContent.isBlank()) {
            return AssistantAnswer.generalKnowledge("Sorry, I could not produce an answer.");
        }

        String json = extractJson(rawContent);
        if (json != null) {
            try {
                AnswerJson dto = GSON.fromJson(json, AnswerJson.class);
                if (dto != null && dto.answer != null && !dto.answer.isBlank()) {
                    List<Citation> citations = filterValidCitations(toCitations(dto.citations), retrieval);
                    boolean grounded = Boolean.TRUE.equals(dto.basedOnCourseMaterial)
                            && !citations.isEmpty()
                            && retrieval.isRelevant();
                    return grounded ? new AssistantAnswer(dto.answer, citations, true)
                            : AssistantAnswer.generalKnowledge(withoutUnverifiedReferences(dto.answer));
                }
            } catch (Exception ignored) {
                // Fall through to the plain-text handling below.
            }
        }

        // The model ignored the JSON instruction: treat the whole text as a
        // general-knowledge answer. Without a validated citation it can never
        // be grounded, regardless of what the retrieval returned.
        return AssistantAnswer.generalKnowledge(withoutUnverifiedReferences(rawContent.trim()));
    }

    /** Drop source-number tokens from ungrounded prose while preserving code and links. */
    private static String withoutUnverifiedReferences(String text) {
        return UNVERIFIED_REFERENCE.matcher(text).replaceAll(match ->
                match.group(1) == null ? "" : Matcher.quoteReplacement(match.group(1)));
    }

    /**
     * Finds the outermost JSON object in the model's output, if any. Handles
     * the common case where the model wraps its JSON in ```json fences or adds
     * a sentence before/after it.
     */
    static String extractJson(String raw) {
        Matcher m = JSON_OBJECT.matcher(raw);
        if (!m.find()) {
            return null;
        }
        String candidate = m.group();
        int last = candidate.lastIndexOf('}');
        if (last >= 0) {
            candidate = candidate.substring(0, last + 1);
        }
        return candidate;
    }

    private static List<Citation> toCitations(List<CitationJson> list) {
        List<Citation> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (CitationJson c : list) {
            if (c == null || c.document == null || c.document.isBlank()) {
                continue;
            }
            out.add(new Citation(c.document, c.pageRange));
        }
        return out;
    }

    /**
     * Keeps only the citations whose document name and page range match a
     * retrieved chunk, so a model cannot cite a source (or page) it was never
     * given.
     */
    private static List<Citation> filterValidCitations(List<Citation> citations, RetrievalResult retrieval) {
        List<RetrievedChunk> chunks = retrieval.getChunks();
        List<Citation> valid = new ArrayList<>();
        for (Citation citation : citations) {
            for (RetrievedChunk chunk : chunks) {
                if (matches(citation, chunk)) {
                    valid.add(citation);
                    break;
                }
            }
        }
        return valid;
    }

    private static boolean matches(Citation citation, RetrievedChunk chunk) {
        return Objects.equals(normalize(citation.getDocumentName()), normalize(chunk.getDocumentName()))
                && Objects.equals(normalize(citation.getPageRange()), normalize(chunk.getPageRange()));
    }

    /**
     * Trims the value and treats {@code null} and blank input as {@code null},
     * so {@code null}, {@code ""} and whitespace compare as equal.
     */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // --- JSON DTOs (shape enforced by the system prompt) ---

    private static final class AnswerJson {
        String answer;
        List<CitationJson> citations;
        Boolean basedOnCourseMaterial;
    }

    private static final class CitationJson {
        String document;
        @SerializedName("pageRange")
        String pageRange;
    }
}
