package com.cst3492.ta.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.sl.usermodel.GroupShape;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.TableShape;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.cst3492.ta.rag.DocumentIngestionException.Reason.*;

/** Extracts only text from physical pages/slides, never notes or OCR guesses. */
final class DocumentExtractor {
    record Page(int number, String text) { }
    record Document(boolean slides, List<Page> pages) { }

    Document extract(Path path) throws DocumentIngestionException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new DocumentIngestionException(UNREADABLE_FILE, "Cannot read document: " + path);
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") && !name.endsWith(".pptx") && !name.endsWith(".ppt")) {
            throw new DocumentIngestionException(UNSUPPORTED_FORMAT,
                    "Supported document formats are PDF, PPTX and PPT: " + path);
        }
        try {
            if (name.endsWith(".pdf")) {
                return pdf(path);
            }
            rejectEncryptedOffice(path);
            return name.endsWith(".pptx") ? pptx(path) : ppt(path);
        } catch (DocumentIngestionException exception) {
            throw exception;
        } catch (InvalidPasswordException | EncryptedDocumentException exception) {
            throw new DocumentIngestionException(ENCRYPTED_DOCUMENT,
                    "Encrypted documents are not supported: " + path, exception);
        } catch (IOException | RuntimeException exception) {
            throw new DocumentIngestionException(MALFORMED_DOCUMENT,
                    "Cannot parse document as its declared format: " + path, exception);
        }
    }

    private Document pdf(Path path) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            if (pdf.isEncrypted()) {
                throw new DocumentIngestionException(ENCRYPTED_DOCUMENT,
                        "Encrypted documents are not supported: " + path);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<Page> pages = new ArrayList<>();
            for (int number = 1; number <= pdf.getNumberOfPages(); number++) {
                stripper.setStartPage(number);
                stripper.setEndPage(number);
                pages.add(new Page(number, stripper.getText(pdf)));
            }
            return new Document(false, List.copyOf(pages));
        }
    }

    private void rejectEncryptedOffice(Path path) throws IOException {
        if (FileMagic.valueOf(path.toFile()) == FileMagic.OLE2) {
            try (POIFSFileSystem fs = new POIFSFileSystem(path.toFile(), true)) {
                if (fs.getRoot().hasEntry("EncryptedPackage") || fs.getRoot().hasEntry("EncryptionInfo")
                        || fs.getRoot().hasEntry("EncryptedSummary")) {
                    throw new DocumentIngestionException(ENCRYPTED_DOCUMENT,
                            "Encrypted documents are not supported: " + path);
                }
            }
        }
    }

    private Document pptx(Path path) throws IOException {
        try (var input = Files.newInputStream(path); XMLSlideShow slides = new XMLSlideShow(input)) {
            List<Page> pages = new ArrayList<>();
            for (var slide : slides.getSlides()) {
                StringBuilder text = new StringBuilder();
                appendShapes(slide.getShapes(), text);
                pages.add(new Page(pages.size() + 1, text.toString()));
            }
            return new Document(true, List.copyOf(pages));
        }
    }

    private Document ppt(Path path) throws IOException {
        try (var input = Files.newInputStream(path); HSLFSlideShow slides = new HSLFSlideShow(input)) {
            List<Page> pages = new ArrayList<>();
            for (var slide : slides.getSlides()) {
                StringBuilder text = new StringBuilder();
                appendShapes(slide.getShapes(), text);
                pages.add(new Page(pages.size() + 1, text.toString()));
            }
            return new Document(true, List.copyOf(pages));
        }
    }

    private void appendShapes(Iterable<? extends Shape<?, ?>> shapes, StringBuilder text) {
        for (Shape<?, ?> shape : shapes) {
            // Tables can also be group shapes. Handle them first to avoid duplicate cells.
            if (shape instanceof TableShape<?, ?> table) {
                for (int row = 0; row < table.getNumberOfRows(); row++) {
                    for (int column = 0; column < table.getNumberOfColumns(); column++) {
                        var cell = table.getCell(row, column);
                        if (cell != null) {
                            append(cell.getText(), text);
                        }
                    }
                }
            } else if (shape instanceof GroupShape<?, ?> group) {
                appendShapes(group.getShapes(), text);
            } else if (shape instanceof TextShape<?, ?> textShape) {
                append(textShape.getText(), text);
            }
        }
    }

    private void append(String value, StringBuilder text) {
        if (value != null && !value.isBlank()) {
            text.append(value).append('\n');
        }
    }
}
