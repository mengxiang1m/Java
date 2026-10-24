package com.cst3492.assistant.ui;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.icons.AllIcons;
import com.intellij.util.IconUtil;
import com.intellij.openapi.util.IconLoader;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;

/** Shared theme-aware surfaces; no global LookAndFeel changes. */
final class ChatUi {
    static final Color BACKGROUND = new JBColor(new Color(0xF5F6F8), new Color(0x1E1F22));
    static final Color SURFACE = new JBColor(Color.WHITE, new Color(0x252629));
    static final Color CARD = new JBColor(new Color(0xEAEDF2), new Color(0x2B2D30));
    static final Color BORDER = new JBColor(new Color(0xD4D8DF), new Color(0x3C3F41));
    static final Color TEXT = new JBColor(new Color(0x22252B), new Color(0xE6E6E6));
    static final Color MUTED = new JBColor(new Color(0x666C76), new Color(0xA0A3A8));
    static final Color ACCENT = new JBColor(new Color(0x245FB5), new Color(0x6EA8FE));
    static final Color ERROR = new JBColor(new Color(0xA52A36), new Color(0xE08A91));

    private ChatUi() { }

    static Font font() {
        Font font = UIManager.getFont("Label.font");
        return font == null ? new Font(Font.DIALOG, Font.PLAIN, 13) : font.deriveFont(Font.PLAIN);
    }

    static void copy(String text) {
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(!isEnabled() ? CARD : primary
                        ? getModel().isPressed() && getModel().isArmed() ? ACCENT.darker()
                        : getModel().isRollover() ? ACCENT.brighter() : ACCENT
                        : getModel().isRollover() ? BORDER : CARD);
                int radius = primary ? getWidth() : JBUI.scale(10);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                if (isFocusOwner()) {
                    g.setColor(primary ? TEXT : ACCENT);
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, radius, radius);
                }
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setContentAreaFilled(false);
        button.setBorder(JBUI.Borders.empty(6, 12));
        button.setForeground(primary ? BACKGROUND : TEXT);
        button.setFont(font());
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    static JButton sendButton() {
        JButton button = button("", true);
        // Resolve the bundled platform asset directly: AllIcons' generated field can
        // be initialized as a dummy before the icon manager starts in plain UI tests.
        var arrow = IconLoader.getIcon("/expui/general/up.svg", AllIcons.class);
        button.setIcon(IconUtil.colorize(arrow, BACKGROUND));
        button.setDisabledIcon(IconUtil.colorize(arrow, MUTED));
        button.setBorder(JBUI.Borders.empty());
        button.setPreferredSize(JBUI.size(38, 38));
        button.setMinimumSize(JBUI.size(38, 38));
        button.setMaximumSize(JBUI.size(38, 38));
        button.setHorizontalAlignment(JButton.CENTER);
        button.setVerticalAlignment(JButton.CENTER);
        button.setToolTipText("Send question");
        button.getAccessibleContext().setAccessibleName("Send question");
        return button;
    }

    static JPanel card(Color background) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), JBUI.scale(12), JBUI.scale(12));
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBackground(background);
        panel.setBorder(JBUI.Borders.empty(12));
        return panel;
    }
}
