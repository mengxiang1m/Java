package com.cst3492.ta.llm;

import com.cst3492.ta.core.AssistantAnswer;
import com.cst3492.ta.core.Citation;
import com.cst3492.ta.core.RetrievedChunk;
import com.cst3492.ta.core.RetrievalResult;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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
 * least one returned citation resolves to a supplied source ID, or matches a
 * retrieved chunk's document name and page range in the legacy format.
 * Citations the model invents are dropped, so a model can never
 * cite a source (or page) it was not given.</p>
 */
public final class AnswerParser {

    private static final Gson GSON = new Gson();
    private static final System.Logger LOG = System.getLogger(AnswerParser.class.getName());
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final Pattern UNVERIFIED_REFERENCE = Pattern.compile(
            "(?s)(```.*?(?:```|\\z)|`[^`\\r\\n]*`)"
                    + "|([ \\t]*)(?<![\\p{L}\\p{N}_\\]])\\[(\\d+)\\](?!\\()");

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
                    boolean numbered = dto.citations != null && dto.citations.stream()
                            .anyMatch(citation -> citation != null && citation.sourceId != null);
                    List<Citation> citations;
                    String content = dto.answer;
                    if (numbered) {
                        var validated = sourceIdCitations(dto.citations, retrieval);
                        citations = validated.citations();
                        content = rewriteReferences(content, validated.numbers());
                    } else {
                        citations = filterValidCitations(toCitations(dto.citations), retrieval);
                    }
                    boolean grounded = Boolean.TRUE.equals(dto.basedOnCourseMaterial)
                            && !citations.isEmpty()
                            && retrieval.isRelevant();
                    if (!grounded && retrieval.isRelevant()) {
                        LOG.log(System.Logger.Level.INFO,
                                "Course citation result: modelDeclaredCourse={0}, retrievedChunks={1}, verifiedSources={2}",
                                dto.basedOnCourseMaterial, retrieval.getChunks().size(), citations.size());
                    }
                    return grounded ? new AssistantAnswer(content, citations, true)
                            : AssistantAnswer.generalKnowledge(withoutUnverifiedReferences(content));
                }
            } catch (Exception ignored) {
                // Fall through to the plain-text handling below.
            }
        }

        // The model ignored the JSON instruction: treat the whole text as a
        // general-knowledge answer. Without a validated citation it can never
        // be grounded, regardless of what the retrieval returned.
        if (retrieval.isRelevant()) {
            LOG.log(System.Logger.Level.WARNING,
                    "Model response did not contain the expected answer JSON; retrievedChunks={0}. Raw response omitted.",
                    retrieval.getChunks().size());
        }
        return AssistantAnswer.generalKnowledge(withoutUnverifiedReferences(rawContent.trim()));
    }

    /** Drop source-number tokens from ungrounded prose while preserving code and links. */
    private static String withoutUnverifiedReferences(String text) {
        return rewriteReferences(text, Map.of());
    }

    private static String rewriteReferences(String text, Map<Integer, Integer> numbers) {
        return UNVERIFIED_REFERENCE.matcher(text).replaceAll(match -> {
            if (match.group(1) != null) return Matcher.quoteReplacement(match.group(1));
            try {
                Integer number = numbers.get(Integer.parseInt(match.group(3)));
                return number == null ? "" : Matcher.quoteReplacement(match.group(2) + "[" + number + "]");
            } catch (NumberFormatException exception) {
                return "";
            }
        });
    }

    private record SourceCitations(List<Citation> citations, Map<Integer, Integer> numbers) { }

    private static SourceCitations sourceIdCitations(List<CitationJson> requested, RetrievalResult retrieval) {
        List<Citation> citations = new ArrayList<>();
        Map<Integer, Integer> numbers = new LinkedHashMap<>();
        for (CitationJson source : requested) {
            if (source == null || source.sourceId == null || source.sourceId < 1
                    || source.sourceId > retrieval.getChunks().size()) continue;
            RetrievedChunk chunk = retrieval.getChunks().get(source.sourceId - 1);
            if (source.document != null && !Objects.equals(normalize(source.document), normalize(chunk.getDocumentName()))) continue;
            if (source.pageRange != null && !Objects.equals(normalize(source.pageRange), normalize(chunk.getPageRange()))) continue;
            int index = 0;
            while (index < citations.size() && !matches(citations.get(index), chunk)) index++;
            if (index == citations.size()) citations.add(new Citation(chunk.getDocumentName(), chunk.getPageRange()));
            numbers.put(source.sourceId, index + 1);
        }
        return new SourceCitations(citations, numbers);
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
        @SerializedName(value = "sourceId", alternate = {"source_id"})
        Integer sourceId;
        String document;
        @SerializedName("pageRange")
        String pageRange;
    }
}
