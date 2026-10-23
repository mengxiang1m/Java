package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatMessage;
import com.cst3492.assistant.chat.ChatReply;
import com.cst3492.assistant.chat.ChatRequest;
import com.cst3492.assistant.chat.ChatService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
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
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultEditorKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/** One in-memory conversation per Tool Window; all UI state belongs to the EDT. */
public final class AssistantChatPanel extends JBPanel<AssistantChatPanel> implements Disposable {
    private static final Logger LOG = Logger.getInstance(AssistantChatPanel.class);

    private final ChatService service;
    private final Project project;
    private final JBTextArea conversation = new JBTextArea(12, 0);
    private final JBScrollPane conversationScroll = new JBScrollPane(conversation,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JBTextArea input = new JBTextArea(4, 20);
    private final JBTextArea contextPreview = new JBTextArea(4, 20);
    private final JBPanel<?> contextPanel = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
    private String editorContext = "";
    private final JButton send = new JButton("Send");
    private final WrappingLabel status = new WrappingLabel("Mock mode — no AI service connected. Enter to send.");
    private boolean sending;
    private boolean disposed;

    public AssistantChatPanel(Project project, ChatService service) {
        super(new BorderLayout(0, JBUI.scale(8)));
        this.service = service;
        this.project = project;
        setBorder(JBUI.Borders.empty(12));

        conversation.setEditable(false);
        conversation.setLineWrap(true);
        conversation.setWrapStyleWord(true);
        // Appending must not move the viewport while the user is reading history.
        ((DefaultCaret) conversation.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        conversation.getAccessibleContext().setAccessibleName("Conversation (mock mode)");
        conversationScroll.setMinimumSize(new Dimension(0, JBUI.scale(60)));
        add(conversationScroll, BorderLayout.CENTER);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.getAccessibleContext().setAccessibleName("Question");
        WrappingLabel inputLabel = new WrappingLabel("Question (Enter to send, Shift+Enter for a new line)");
        inputLabel.setLabelFor(input);
        JBPanel<?> composer = new JBPanel<>(new BorderLayout(0, JBUI.scale(6)));
        JBScrollPane inputScroll = new JBScrollPane(input,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.setMinimumSize(new Dimension(0, JBUI.scale(40)));
        composer.add(inputScroll, BorderLayout.CENTER);
        contextPreview.setEditable(false);
        contextPreview.setLineWrap(true);
        // Character wrapping handles long identifiers as well as normally spaced code.
        // These are visual breaks only; the document and editorContext are unchanged.
        contextPreview.setWrapStyleWord(false);
        contextPreview.getAccessibleContext().setAccessibleName("Selected editor code (not executed)");
        JBPanel<?> contextHeader = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        contextHeader.add(new WrappingLabel("Editor code context (not executed)"), BorderLayout.NORTH);
        JButton clearContext = new JButton("Clear context");
        clearContext.addActionListener(event -> {
            editorContext = "";
            contextPreview.setText("");
            contextPanel.setVisible(false);
            composer.revalidate();
            composer.repaint();
            input.requestFocusInWindow();
        });
        JBPanel<?> contextActions = new JBPanel<>(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        contextActions.add(clearContext);
        contextHeader.add(contextActions, BorderLayout.SOUTH);
        contextPanel.add(contextHeader, BorderLayout.NORTH);
        JBScrollPane contextScroll = new JBScrollPane(contextPreview,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        contextScroll.setMinimumSize(new Dimension(0, JBUI.scale(40)));
        contextPanel.add(contextScroll, BorderLayout.CENTER);
        contextPanel.setVisible(false);
        JBPanel<?> composerHeader = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        composerHeader.add(contextPanel, BorderLayout.CENTER);
        composerHeader.add(inputLabel, BorderLayout.SOUTH);
        composer.add(composerHeader, BorderLayout.NORTH);
        JBPanel<?> actions = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        JBPanel<?> sendActions = new JBPanel<>(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        sendActions.add(send);
        actions.add(sendActions, BorderLayout.NORTH);
        actions.add(status, BorderLayout.SOUTH);
        composer.add(actions, BorderLayout.SOUTH);
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

    /** Called on the EDT after the existing Tool Window has been activated. Never sends. */
    public void setEditorContext(String selectedCode) {
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
        if (disposed || project.isDisposed() || sending) {
            return;
        }
        String question = input.getText().strip();
        if (question.isBlank()) {
            status.setText("Please enter a question before sending.");
            input.requestFocusInWindow();
            return;
        }
        ChatRequest request = new ChatRequest(question, editorContext);

        sending = true;
        send.setEnabled(false);
        input.setEditable(false);
        append(new ChatMessage(ChatMessage.Role.USER, question));
        if (!request.editorContext().isEmpty()) {
            append(new ChatMessage(ChatMessage.Role.EDITOR_CONTEXT, request.editorContext()));
        }
        input.setText("");
        status.setText("Sending…");

        // Yield to the UI event queue; the mock only returns small local fixtures.
        // This is not a background worker: never add blocking work to this callback.
        ToolWindowManager.getInstance(project).invokeLater(() -> {
            if (disposed || project.isDisposed()) {
                return;
            }
            try {
                ChatReply reply = service.reply(request);
                append(ChatReplyPresentation.message(reply));
                status.setText(ChatReplyPresentation.statusText(reply));
            } catch (RuntimeException exception) {
                LOG.warn("Chat service failed", exception);
                ChatReply error = new ChatReply("The service could not reply. Please try again.",
                        ChatReply.Status.ERROR, List.of());
                append(ChatReplyPresentation.message(error));
                status.setText(ChatReplyPresentation.statusText(error));
            } finally {
                sending = false;
                send.setEnabled(true);
                input.setEditable(true);
                input.requestFocusInWindow();
            }
        });
    }

    private void append(ChatMessage message) {
        JScrollBar scrollBar = conversationScroll.getVerticalScrollBar();
        boolean followingLatest = scrollBar.getValue() + scrollBar.getVisibleAmount()
                >= scrollBar.getMaximum() - JBUI.scale(4);
        conversation.append(message.role().label() + ":\n" + message.text() + "\n\n");
        if (followingLatest) {
            conversation.setCaretPosition(conversation.getDocument().getLength());
        }
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
