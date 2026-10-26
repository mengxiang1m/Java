package com.cst3492.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RealCourseAcceptanceTest {
    @TempDir Path directory;

    @Test void onlyExplicitCoursePdfsAreSelectedAndAssignmentIsExcluded() throws Exception {
        var course = Files.createFile(directory.resolve("Lec-1-Java-Fundamentals.pdf"));
        Files.createFile(directory.resolve("AY202627S1-CST3492-Assignment-A.pdf"));
        Files.createFile(directory.resolve("notes.pdf"));
        Files.createDirectories(directory.resolve("Lec-folder.pdf"));
        assertEquals(List.of(course), RealCourseAcceptance.courseFiles(directory));
    }

    @Test void correctnessRequiresBothTheOriginalDocumentAndPhysicalPage() {
        var question = RealCourseAcceptance.QUESTIONS.stream().filter(q -> q.id().equals("exceptions")).findFirst().orElseThrow();
        assertTrue(RealCourseAcceptance.matches(question, directory.resolve("Lec-3.1-Exceptions.pdf").toString(), "p.5"));
        assertFalse(RealCourseAcceptance.matches(question, directory.resolve("Lec-3.1-Exceptions.pdf").toString(), "p.4"));
        assertFalse(RealCourseAcceptance.matches(question, directory.resolve("another.pdf").toString(), "p.5"));
    }
}
