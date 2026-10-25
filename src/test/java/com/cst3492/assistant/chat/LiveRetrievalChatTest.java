package com.cst3492.assistant.chat;

import com.cst3492.ta.llm.AnswerGenerationService;
import com.cst3492.ta.llm.LlmConfig;
import com.cst3492.ta.rag.KnowledgeBaseService;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiveRetrievalChatTest {
    @TempDir Path temp;

    @Test
    void eachQuestionRetrievesItsOwnMaterialAndKeepsExactEditorContext() throws Exception {
        Path document = slides("Lecture.pptx", "JDBC transaction rollback", "Servlet lifecycle init destroy");
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(document);
        List<String> prompts = new ArrayList<>();
        var generation = new AnswerGenerationService(LlmConfig.deepseek("unused-test-key"), (config, prompt) -> {
            prompts.add(prompt.getUser());
            return "A general answer";
        });
        var service = new LlmChatService(generation, knowledge);
        String code = "\tclass Demo {\r\n    int count;\n}\n";

        service.reply(new ChatRequest("JDBC", code));
        service.reply("Servlet");

        assertTrue(prompts.get(0).contains("JDBC transaction rollback"));
        assertFalse(prompts.get(0).contains("Servlet lifecycle init destroy"));
        assertTrue(prompts.get(0).contains(code));
        assertTrue(prompts.get(1).contains("Servlet lifecycle init destroy"));
        assertFalse(prompts.get(1).contains("JDBC transaction rollback"));
    }

    @Test
    void sameChatSeesLaterImportsAndRemovals() throws Exception {
        var knowledge = new KnowledgeBaseService();
        List<String> prompts = new ArrayList<>();
        var generation = new AnswerGenerationService(LlmConfig.deepseek("unused-test-key"), (config, prompt) -> {
            prompts.add(prompt.getUser());
            return "A general answer";
        });
        var service = new LlmChatService(generation, knowledge);

        service.reply("JDBC");
        Path document = slides("Lecture.pptx", "JDBC transaction rollback");
        knowledge.ingest(document);
        service.reply("JDBC");
        knowledge.removeDocument(document);
        service.reply("JDBC");

        assertTrue(prompts.get(0).contains("No course material was retrieved"));
        assertTrue(prompts.get(1).contains("JDBC transaction rollback"));
        assertTrue(prompts.get(2).contains("No course material was retrieved"));
    }

    private Path slides(String name, String... contents) throws Exception {
        Path path = temp.resolve(name);
        try (var show = new XMLSlideShow(); var output = Files.newOutputStream(path)) {
            for (String content : contents) {
                show.createSlide().createTextBox().setText(content);
            }
            show.write(output);
        }
        return path;
    }
}
