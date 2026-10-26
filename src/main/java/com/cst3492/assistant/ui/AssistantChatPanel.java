package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatMessage;
import com.cst3492.assistant.chat.ChatRequest;
import com.cst3492.assistant.chat.ChatReply;
import com.cst3492.assistant.chat.ChatService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JScrollBar;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.JProgressBar;
import javax.swing.text.DefaultEditorKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/** One in-memory conversation per Tool Window; all UI state belongs to the EDT. */
public final class AssistantChatPanel extends JBPanel<AssistantChatPanel> implements Disposable {

    private final ChatService service;
    private final Project project;
    private final ConversationView conversation = new ConversationView();
    private final JBScrollPane conversationScroll = new JBScrollPane(conversation,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JBTextArea input = new JBTextArea(4, 20);
    private final JBTextArea contextPreview = new JBTextArea(4, 20);
    private final JBPanel<?> contextPanel = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
    private String editorContext = "";
    private final JButton send = ChatUi.sendButton();
    private final ChatComposer unifiedComposer;
    private final JProgressBar loading = new JProgressBar();
    private final WrappingLabel status = new WrappingLabel("Mock mode — no AI service connected.");
    private boolean sending;
    private boolean restoringComposer;
    private volatile boolean disposed;
    private ChatRequestWorker worker;
    // Compatibility path for main's standalone import callers; production uses the project service.
    private final com.cst3492.ta.rag.KnowledgeBaseService directKnowledge;
    private final JButton importMaterials;
    private final JBTextArea importDetails = new JBTextArea(3, 20);
    private final JBScrollPane importDetailsScroll = new JBScrollPane(importDetails);
    private DocumentImportWorker importWorker;
    private final com.cst3492.assistant.knowledge.ProjectKnowledgeBase knowledge;
    private final java.util.List<java.nio.file.Path> attachments = new java.util.ArrayList<>();
    private final JButton attach = ChatUi.button("+", false);

    public AssistantChatPanel(Project project, ChatService service) {
        this(project, service, "Chat service ready.");
    }

    public AssistantChatPanel(Project project, ChatService service, String modeDescription) {
        this(project, service, modeDescription, (com.cst3492.assistant.knowledge.ProjectKnowledgeBase) null);
    }

    public AssistantChatPanel(Project project, ChatService service, String modeDescription,
                              com.cst3492.assistant.knowledge.ProjectKnowledgeBase knowledge) {
        this(project, service, modeDescription, knowledge, null);
    }

    /** Retains main's shared standalone knowledge-base constructor and immediate import API. */
    public AssistantChatPanel(Project project, ChatService service, String modeDescription,
                              com.cst3492.ta.rag.KnowledgeBaseService knowledge) {
        this(project, service, modeDescription, null, knowledge);
    }

    private AssistantChatPanel(Project project, ChatService service, String modeDescription,
                               com.cst3492.assistant.knowledge.ProjectKnowledgeBase knowledge,
                               com.cst3492.ta.rag.KnowledgeBaseService directKnowledge) {
        super(new BorderLayout(0, JBUI.scale(8)));
        this.service = service;
        this.project = project;
        this.knowledge = knowledge;
        this.directKnowledge = directKnowledge;
        status.setText(modeDescription);
        status.setForeground(ChatUi.MUTED);
        status.setFont(ChatUi.font().deriveFont(Math.max(10f, ChatUi.font().getSize() - 1f)));
        setBackground(ChatUi.BACKGROUND);
        setBorder(JBUI.Borders.empty(12));

        conversationScroll.setBorder(null);
        conversationScroll.getViewport().setBackground(ChatUi.BACKGROUND);
        conversationScroll.setMinimumSize(new Dimension(0, JBUI.scale(60)));
        add(conversationScroll, BorderLayout.CENTER);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.getAccessibleContext().setAccessibleName("Question");
        input.setBackground(ChatUi.SURFACE);
        input.setForeground(ChatUi.TEXT);
        input.setCaretColor(ChatUi.ACCENT);
        input.setFont(ChatUi.font());
        JBPanel<?> composer = new JBPanel<>(new BorderLayout(0, JBUI.scale(3)));
        composer.setOpaque(false);
        composer.setBorder(JBUI.Borders.emptyTop(6));
        unifiedComposer = new ChatComposer(input, send);
        if (knowledge != null) {
            attach.setPreferredSize(JBUI.size(32, 32));
            attach.setMinimumSize(JBUI.size(32, 32));
            attach.setBorder(JBUI.Borders.empty());
            attach.putClientProperty("assistant.quiet", true);
            attach.setToolTipText("Attach PDF, PPTX or PPT; imported when you send");
            attach.getAccessibleContext().setAccessibleName("Attach course files");
            attach.addActionListener(event -> chooseAttachments());
            unifiedComposer.addAttachmentAction(attach);
        }
        importMaterials = directKnowledge == null ? null : ChatUi.button("Import materials", false);
        if (importMaterials != null) {
            importMaterials.setToolTipText("Import PDF, PPTX or PPT course materials (up to 50 MiB per file)");
            importMaterials.addActionListener(event -> chooseMaterials());
            unifiedComposer.addAttachmentAction(importMaterials);
        }
        composer.add(unifiedComposer, BorderLayout.CENTER);
        contextPreview.setEditable(false);
        contextPreview.setRows(2);
        contextPreview.setBackground(ChatUi.SURFACE);
        contextPreview.setForeground(ChatUi.TEXT);
        contextPreview.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, ChatUi.font().getSize()));
        contextPreview.setBorder(JBUI.Borders.empty(8));
        contextPanel.setOpaque(false);
        contextPreview.setLineWrap(true);
        // Character wrapping handles long identifiers as well as normally spaced code.
        // These are visual breaks only; the document and editorContext are unchanged.
        contextPreview.setWrapStyleWord(false);
        contextPreview.getAccessibleContext().setAccessibleName("Selected editor code (not executed)");
        JBPanel<?> contextHeader = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        contextHeader.setOpaque(false);
        WrappingLabel contextLabel = new WrappingLabel("Attached Java code");
        contextLabel.setForeground(ChatUi.ACCENT);
        contextHeader.add(contextLabel, BorderLayout.CENTER);
        JButton clearContext = ChatUi.button("Clear context", false);
        clearContext.addActionListener(event -> {
            if (!canUpdateUi()) {
                return;
            }
            editorContext = "";
            contextPreview.setText("");
            contextPanel.setVisible(false);
            status.setText("Code context cleared. Your question draft is unchanged.");
            composer.revalidate();
            composer.repaint();
            input.requestFocusInWindow();
        });
        JBPanel<?> contextActions = new JBPanel<>(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        contextActions.setOpaque(false);
        contextActions.add(clearContext);
        contextHeader.add(contextActions, BorderLayout.EAST);
        contextPanel.add(contextHeader, BorderLayout.NORTH);
        JBScrollPane contextScroll = new JBScrollPane(contextPreview,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        contextScroll.setMinimumSize(new Dimension(0, JBUI.scale(40)));
        contextScroll.setBorder(JBUI.Borders.customLine(ChatUi.BORDER));
        contextPanel.add(contextScroll, BorderLayout.CENTER);
        contextPanel.setVisible(false);
        composer.add(contextPanel, BorderLayout.NORTH);
        JBPanel<?> feedback = new JBPanel<>(new BorderLayout(0, JBUI.scale(6)));
        feedback.setOpaque(false);
        loading.setIndeterminate(true);
        loading.setVisible(false);
        loading.setForeground(ChatUi.ACCENT);
        loading.setPreferredSize(new Dimension(0, JBUI.scale(3)));
        loading.getAccessibleContext().setAccessibleName("Request in progress");
        feedback.add(loading, BorderLayout.NORTH);
        if (directKnowledge != null) {
            JBPanel<?> feedbackText = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
            feedbackText.setOpaque(false);
            feedbackText.add(status, BorderLayout.NORTH);
            importDetails.setEditable(false);
            importDetails.setLineWrap(true);
            importDetails.setWrapStyleWord(true);
            importDetails.getAccessibleContext().setAccessibleName("Course material import details");
            importDetailsScroll.setVisible(false);
            importDetailsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            feedbackText.add(importDetailsScroll, BorderLayout.CENTER);
            feedback.add(feedbackText, BorderLayout.CENTER);
        } else {
            feedback.add(status, BorderLayout.CENTER);
        }
        composer.add(feedback, BorderLayout.SOUTH);
        add(composer, BorderLayout.SOUTH);

        send.addActionListener(event -> sendQuestion());
        input.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendQuestion");
        // Reuse Swing's editing action: inserts at the caret (or replaces the selection).
        input.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                DefaultEditorKit.insertBreakAction);
        input.getActionMap().put("sendQuestion", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                sendQuestion();
            }
        });
    }

    public JComponent getInput() {
        return input;
    }

    private void chooseMaterials() {
        if (!canUpdateUi() || sending || restoringComposer || importWorker != null) {
            return;
        }
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, true);
        descriptor.setTitle("Import course materials");
        descriptor.setDescription("Select PDF, PPTX or PPT files, up to 50 MiB each. Materials stay in this session.");
        descriptor.withFileFilter(file -> {
            String extension = file.getExtension();
            return extension != null && List.of("pdf", "pptx", "ppt").contains(extension.toLowerCase(Locale.ROOT));
        });
        FileChooser.chooseFiles(descriptor, project, null,
                files -> importDocuments(files.stream().map(file -> file.toNioPath()).toList()));
    }

    /** Uses the same knowledge base as live chat. The caller invokes this on the EDT. */
    public void importDocuments(List<Path> paths) {
        requireEdt();
        if (directKnowledge == null || paths.isEmpty() || !canUpdateUi() || sending || restoringComposer || importWorker != null) {
            return;
        }
        importMaterials.setEnabled(false);
        send.setEnabled(false);
        status.setText("Importing course materials…");
        loading.setVisible(true);
        importDetailsScroll.setVisible(false);
        importWorker = new DocumentImportWorker(directKnowledge, paths,
                pending -> pending == importWorker && canUpdateUi(), summary -> {
                    status.setText(summary.message());
                    String details = String.join("\n", summary.problems());
                    if (summary.withoutText() > 0) {
                        details = "Image-only or blank documents need selectable text; OCR is not included.\n" + details;
                    }
                    importDetails.setText(details.strip());
                    importDetails.setCaretPosition(0);
                    importDetailsScroll.setVisible(!details.isBlank());
                    revalidate();
                }, this::finishImport);
        importWorker.execute();
    }

    private void finishImport(DocumentImportWorker finishedWorker) {
        requireEdt();
        if (importWorker != finishedWorker) {
            return;
        }
        importWorker = null;
        if (canUpdateUi()) {
            restoringComposer = true;
            try {
                loading.setVisible(false);
                importMaterials.setEnabled(true);
                if (canUpdateUi()) {
                    send.setEnabled(true);
                }
            } finally {
                restoringComposer = false;
            }
        }
    }

    private void chooseAttachments() {
        if (!canUpdateUi() || sending) return;
        var descriptor = new com.intellij.openapi.fileChooser.FileChooserDescriptor(true, false, false, false, false, true)
                .withTitle("Attach course PDF, PPTX or PPT")
                .withFileFilter(file -> file.getExtension() != null && java.util.Set.of("pdf", "ppt", "pptx")
                        .contains(file.getExtension().toLowerCase(java.util.Locale.ROOT)));
        var files = com.intellij.openapi.fileChooser.FileChooser.chooseFiles(descriptor, project, null);
        setAttachments(java.util.Arrays.stream(files).map(file -> file.toNioPath()).toList());
    }

    void setAttachments(java.util.List<java.nio.file.Path> paths) {
        requireEdt();
        if (!canUpdateUi() || sending || knowledge == null) return;
        for (var path : paths) {
            var normalized = path.toAbsolutePath().normalize();
            if (!attachments.contains(normalized)) attachments.add(normalized);
        }
        refreshAttachments();
    }

    private void refreshAttachments() {
        unifiedComposer.showAttachments(java.util.List.copyOf(attachments), path -> {
            if (canUpdateUi() && !sending) { attachments.remove(path); refreshAttachments(); }
        }, !sending);
    }

    /** Called on the EDT after the existing Tool Window has been activated. Never sends. */
    public void setEditorContext(String selectedCode) {
        requireEdt();
        if (disposed || project.isDisposed() || !ChatRequest.hasSelectedCode(selectedCode)) {
            return;
        }
        editorContext = selectedCode;
        contextPreview.setText(selectedCode);
        contextPreview.setCaretPosition(0);
        contextPanel.setVisible(true);
        revalidate();
        repaint();
        input.requestFocusInWindow();
    }

    private void sendQuestion() {
        requireEdt();
        if (disposed || project.isDisposed() || sending || restoringComposer || importWorker != null) {
            return;
        }
        String question = input.getText();
        if (question.isBlank()) {
            status.setText("Please enter a question before sending.");
            input.requestFocusInWindow();
            return;
        }
        ChatRequest request = new ChatRequest(question, editorContext);
        var submitted = java.util.List.copyOf(attachments);

        sending = true;
        if (importMaterials != null) importMaterials.setEnabled(false);
        attach.setEnabled(false);
        refreshAttachments();
        send.setEnabled(false);
        input.setEditable(false);
        append(new ChatMessage(ChatMessage.Role.USER, question));
        if (!request.editorContext().isEmpty()) {
            append(new ChatMessage(ChatMessage.Role.EDITOR_CONTEXT, request.editorContext()));
        }
        input.setText("");
        status.setText("Sending…");
        loading.setVisible(true);

        ChatService submission = current -> {
            for (var path : submitted) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                try {
                    if (java.nio.file.Files.size(path) > DocumentImportWorker.MAX_FILE_BYTES) {
                        return new ChatReply(path.getFileName() + ": exceeds the 50 MiB file limit.",
                                ChatReply.Status.ERROR, java.util.List.of());
                    }
                    var identity = path.toRealPath();
                    boolean imported = knowledge.snapshot().documents().stream()
                            .anyMatch(report -> report.path().equals(identity));
                    if (!imported) knowledge.ingest(identity);
                } catch (java.util.concurrent.CancellationException cancelled) {
                    throw cancelled;
                } catch (java.io.IOException failure) {
                    return new ChatReply(path.getFileName() + ": " + KnowledgeBasePanel.failureMessage(failure),
                            ChatReply.Status.ERROR, java.util.List.of());
                } catch (RuntimeException failure) {
                    return new ChatReply(path.getFileName() + ": " + KnowledgeBasePanel.failureMessage(failure),
                            ChatReply.Status.ERROR, java.util.List.of());
                }
            }
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            return service.reply(current);
        };
        if (!submitted.isEmpty()) status.setText("Importing attachments, then answering…");
        worker = new ChatRequestWorker(submission, request, pending -> pending == worker && canUpdateUi(), reply -> {
            if (reply.status() != ChatReply.Status.ERROR) attachments.removeAll(submitted);
            appendReply(reply, request, submitted);
            if (canUpdateUi()) {
                status.setText(ChatReplyPresentation.statusText(reply));
            }
        }, () -> status.setText("Request cancelled. You can send another question."), this::finishSending);
        worker.execute();
    }

    private boolean canUpdateUi() {
        return !disposed && !project.isDisposed();
    }

    private void finishSending(ChatRequestWorker finishedWorker) {
        requireEdt();
        if (worker != finishedWorker) {
            return; // A cancelled/late request must never clear a newer request's state.
        }
        worker = null;
        sending = false;
        if (!canUpdateUi()) {
            return;
        }
        restoringComposer = true;
        try {
            loading.setVisible(false);
            if (!canRestoreComposer()) return;
            refreshAttachments();
            if (!canRestoreComposer()) return;
            attach.setEnabled(true);
            if (!canRestoreComposer()) return;
            if (canRestoreComposer() && importMaterials != null) importMaterials.setEnabled(true);
            if (!canRestoreComposer()) return;
            send.setEnabled(true);
            if (!canRestoreComposer()) {
                return;
            }
            input.setEditable(true);
            if (canRestoreComposer()) {
                input.requestFocusInWindow();
            }
        } finally {
            restoringComposer = false;
        }
    }

    private boolean canRestoreComposer() {
        // Swing property listeners can dispose the panel reentrantly during restoration.
        return canUpdateUi() && worker == null && !sending;
    }

    private void append(ChatMessage message) {
        appendCard(message, null, null);
    }

    private void appendReply(ChatReply reply, ChatRequest request, java.util.List<java.nio.file.Path> submitted) {
        appendCard(ChatReplyPresentation.cardMessage(reply), reply, () -> {
            if (!canUpdateUi() || sending || restoringComposer || importWorker != null) {
                return;
            }
            editorContext = request.editorContext();
            contextPreview.setText(editorContext);
            contextPreview.setCaretPosition(0);
            contextPanel.setVisible(!editorContext.isEmpty());
            input.setText(request.question());
            attachments.clear();
            attachments.addAll(submitted);
            refreshAttachments();
            sendQuestion();
        });
    }

    private void appendCard(ChatMessage message, ChatReply reply, Runnable retry) {
        JScrollBar scrollBar = conversationScroll.getVerticalScrollBar();
        int previousPosition = scrollBar.getValue();
        boolean followingLatest = scrollBar.getValue() + scrollBar.getVisibleAmount()
                >= scrollBar.getMaximum() - JBUI.scale(4);
        conversation.append(message, reply, retry);
        if (followingLatest) {
            SwingUtilities.invokeLater(() -> {
                if (canUpdateUi() && scrollBar.getValue() == previousPosition) {
                    scrollBar.setValue(scrollBar.getMaximum());
                }
            });
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        // Disposer callers need not be on the EDT. Mark inactive immediately, then
        // serialize bookkeeping/cancellation with done(), without touching disposed UI.
        if (SwingUtilities.isEventDispatchThread()) {
            cancelPendingRequest();
        } else {
            SwingUtilities.invokeLater(this::cancelPendingRequest);
        }
    }

    private void cancelPendingRequest() {
        requireEdt();
        conversation.dispose();
        DocumentImportWorker pendingImport = importWorker;
        importWorker = null;
        if (pendingImport != null) pendingImport.cancel(true);
        ChatRequestWorker pending = worker;
        worker = null;
        sending = false;
        if (pending != null) {
            // cancel(true) may invoke done() synchronously on the EDT; detach first.
            pending.cancel(true);
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Chat UI lifecycle must run on the EDT");
        }
    }
}
