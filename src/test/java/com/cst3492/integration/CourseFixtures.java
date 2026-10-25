package com.cst3492.integration;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;

import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Actual local documents for integration tests and clearly labelled demonstration material. */
public final class CourseFixtures {
    private CourseFixtures() { }

    static Path write(Path path, String... pages) throws Exception {
        String name = path.toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            try (var document = new PDDocument()) {
                var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                for (String text : pages) {
                    var page = new PDPage();
                    document.addPage(page);
                    if (text.isBlank()) continue;
                    try (var content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(font, 12);
                        content.setLeading(16);
                        content.newLineAtOffset(40, 720);
                        for (String line : text.split("\n")) {
                            content.showText(line);
                            content.newLine();
                        }
                        content.endText();
                    }
                }
                document.save(path.toFile());
            }
        } else if (name.endsWith(".pptx")) {
            try (var show = new XMLSlideShow(); var output = Files.newOutputStream(path)) {
                for (String text : pages) {
                    var slide = show.createSlide();
                    if (!text.isBlank()) {
                        var box = slide.createTextBox();
                        box.setAnchor(new Rectangle2D.Double(20, 20, 600, 300));
                        box.setText(text);
                    }
                }
                show.write(output);
            }
        } else if (name.endsWith(".ppt")) {
            try (var show = new HSLFSlideShow(); var output = Files.newOutputStream(path)) {
                for (String text : pages) {
                    var slide = show.createSlide();
                    if (!text.isBlank()) {
                        var box = slide.createTextBox();
                        box.setAnchor(new Rectangle2D.Double(20, 20, 600, 300));
                        box.setText(text);
                    }
                }
                show.write(output);
            }
        } else throw new IllegalArgumentException("Unsupported fixture format");
        return path;
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createDirectories(Path.of(args[0]));
        String jdbc = "DEMO COURSE MATERIAL (generated for acceptance)\n"
                + "JDBC transactions: disable auto-commit for a transaction.\n"
                + "Use commit on success and rollback on failure.";
        String servlet = "DEMO COURSE MATERIAL (generated for acceptance)\n"
                + "Servlet lifecycle: init, service, destroy.\n"
                + "HttpSession stores session data across requests.";
        write(directory.resolve("JavaEnterprise-Demo.pdf"), "", jdbc, servlet);
        write(directory.resolve("JavaEnterprise-Demo.pptx"), "", jdbc, servlet);
        write(directory.resolve("JavaEnterprise-Demo.ppt"), "", jdbc, servlet);
        Files.writeString(directory.resolve("Demo.java"), """
                import java.sql.Connection;

                // Demo code for right-click context acceptance; no database connection is made.
                class Demo {
                    void finishTransaction(Connection connection, boolean success) throws Exception {
                        if (success) connection.commit();
                        else connection.rollback();
                    }
                }
                """);
        System.out.println("Acceptance demo documents and Demo.java: " + directory.toAbsolutePath());
    }
}
