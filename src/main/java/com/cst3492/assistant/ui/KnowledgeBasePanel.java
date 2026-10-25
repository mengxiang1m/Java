package com.cst3492.assistant.ui;

import com.cst3492.assistant.knowledge.ProjectKnowledgeBase;
import com.cst3492.ta.rag.DocumentIngestionException;
import com.cst3492.ta.rag.IngestionReport;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/** Small independent toolbar: selecting a file never parses it on the EDT. */
final class KnowledgeBasePanel extends JPanel implements Disposable {
    private final Project project;
    private final ProjectKnowledgeBase knowledge;
    private final JButton importButton = ChatUi.button("Import course file", false);
    private final JButton removeButton = ChatUi.button("Remove", false);
    private final JComboBox<IngestionReport> documents = new JComboBox<>();
    private final WrappingLabel feedback = new WrappingLabel("Course library: not initialized (in memory only).");
    private SwingWorker<String, Void> worker;
    private volatile boolean disposed;

    KnowledgeBasePanel(Project project, ProjectKnowledgeBase knowledge) {
        super(new BorderLayout(0, 4));
        this.project = project;
        this.knowledge = knowledge;
        setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        actions.setOpaque(false);
        actions.add(importButton);
        actions.add(removeButton);
        add(actions, BorderLayout.NORTH);
        documents.setPrototypeDisplayValue(new IngestionReport(Path.of("course.pdf"), "course.pdf", 0, 0));
        documents.setMinimumSize(new Dimension(0, 24));
        documents.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof IngestionReport report) {
                    label.setText(report.path().getFileName() + " (" + report.pageCount() + " pages/slides, "
                            + report.chunkCount() + " chunks)");
                    label.setToolTipText(report.documentName());
                }
                return label;
            }
        });
        documents.addActionListener(event -> {
            if (documents.getSelectedItem() instanceof IngestionReport report)
                documents.setToolTipText(report.documentName());
        });
        add(documents, BorderLayout.CENTER);
        feedback.setForeground(ChatUi.MUTED);
        add(feedback, BorderLayout.SOUTH);
        importButton.addActionListener(event -> choose());
        removeButton.addActionListener(event -> {
            if (documents.getSelectedItem() instanceof IngestionReport report) {
                start(() -> knowledge.removeDocument(report.path()) ? "Document removed." : "Document no longer exists in library.");
            }
        });
        refresh();
        feedback.setText(switch (knowledge.snapshot().state()) {
            case UNINITIALIZED -> "Course library: not initialized (in memory only).";
            case EMPTY -> "Course library: no searchable content (in memory only).";
            case READY -> "Course library: " + documents.getItemCount() + " documents (in memory only).";
            case CLOSED -> "Course library closed.";
        });
    }

    private void choose() {
        if (!live() || worker != null) return;
        var descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle("Import course PDF, PPTX or PPT")
                .withFileFilter(file -> {
                    String extension = file.getExtension();
                    return extension != null && java.util.Set.of("pdf", "pptx", "ppt")
                            .contains(extension.toLowerCase(java.util.Locale.ROOT));
                });
        var file = FileChooser.chooseFile(descriptor, project, null);
        if (file != null && live()) {
            Path path = file.toNioPath();
            start(() -> {
                IngestionReport report = knowledge.ingest(path);
                return "Imported " + report.path().getFileName() + ": " + report.pageCount()
                        + " pages/slides, " + report.chunkCount() + " chunks."
                        + (report.hasExtractableText() ? "" : " No extractable text; OCR is not supported.");
            });
        }
    }

    void start(Operation operation) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Library UI must run on EDT");
        if (!live() || worker != null) return;
        importButton.setEnabled(false);
        removeButton.setEnabled(false);
        feedback.setText("Updating course library…");
        worker = new SwingWorker<>() {
            @Override protected String doInBackground() throws Exception { return operation.run(); }
            @Override protected void done() {
                if (worker != this) return;
                worker = null;
                if (!live()) return;
                try {
                    feedback.setText(get());
                } catch (CancellationException exception) {
                    feedback.setText("Library operation cancelled.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    feedback.setText("Library operation interrupted.");
                } catch (ExecutionException exception) {
                    feedback.setText(failureMessage(exception.getCause()));
                } finally {
                    if (live()) refresh();
                }
            }
        };
        worker.execute();
    }

    static String failureMessage(Throwable failure) {
        if (failure instanceof DocumentIngestionException ingestion) {
            return switch (ingestion.getReason()) {
                case UNSUPPORTED_FORMAT -> "Import failed: only PDF, PPTX and PPT are supported.";
                case UNREADABLE_FILE -> "Import failed: the file cannot be read.";
                case ENCRYPTED_DOCUMENT -> "Import failed: encrypted documents are not supported.";
                case MALFORMED_DOCUMENT -> "Import failed: the document is damaged or cannot be parsed.";
            };
        }
        return "Library update failed. Check the local file and try again.";
    }

    private void refresh() {
        var selected = documents.getSelectedItem();
        documents.removeAllItems();
        knowledge.snapshot().documents().forEach(documents::addItem);
        if (selected != null) documents.setSelectedItem(selected);
        importButton.setEnabled(true);
        removeButton.setEnabled(documents.getItemCount() > 0);
    }

    private boolean live() { return !disposed && !project.isDisposed(); }

    @Override public void dispose() {
        disposed = true;
        Runnable cancel = () -> {
            var pending = worker;
            worker = null;
            if (pending != null) pending.cancel(true);
        };
        if (SwingUtilities.isEventDispatchThread()) cancel.run();
        else SwingUtilities.invokeLater(cancel);
    }

    @FunctionalInterface interface Operation { String run() throws Exception; }
}
