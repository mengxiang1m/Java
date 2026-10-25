package com.cst3492.ta.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xslf.usermodel.XMLSlideShow;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Small real files, generated locally; tests do not depend on course files or network access. */
final class TestDocuments {
    private TestDocuments() { }

    static Path write(Path path, String... pages) throws IOException {
        String name = path.toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return pdf(path, false, pages);
        }
        if (name.endsWith(".pptx")) {
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
        } else {
            throw new IllegalArgumentException("Unknown fixture format: " + path);
        }
        return path;
    }

    static Path pdf(Path path, boolean encrypted, String... pages) throws IOException {
        try (var pdf = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                var page = new PDPage();
                pdf.addPage(page);
                if (!text.isBlank()) {
                    try (var content = new PDPageContentStream(pdf, page)) {
                        content.beginText();
                        content.setFont(font, 12);
                        content.newLineAtOffset(40, 720);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            if (encrypted) {
                var protection = new StandardProtectionPolicy("owner-password", "secret", new AccessPermission());
                protection.setEncryptionKeyLength(128);
                pdf.protect(protection);
            }
            pdf.save(path.toFile());
        }
        return path;
    }

    static Path encryptedPptx(Path path) throws Exception {
        try (var show = new XMLSlideShow(); var fs = new POIFSFileSystem()) {
            show.createSlide().createTextBox().setText("Secret material");
            var encryptor = new EncryptionInfo(EncryptionMode.agile).getEncryptor();
            encryptor.confirmPassword("secret");
            try (var encrypted = encryptor.getDataStream(fs)) {
                show.write(encrypted);
            }
            try (var output = Files.newOutputStream(path)) {
                fs.writeFilesystem(output);
            }
        }
        return path;
    }
}
