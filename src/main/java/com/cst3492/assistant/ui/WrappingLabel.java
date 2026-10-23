package com.cst3492.assistant.ui;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;

/** A themed Swing label whose preferred height follows its parent's available width. */
final class WrappingLabel extends JLabel {
    WrappingLabel(String text) {
        super(text);
    }

    @Override
    public void setText(String text) {
        // Labels accept plain text only; never interpret service text as HTML markup.
        super.setText(text == null ? null : "<html>" + text.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</html>");
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension naturalSize = super.getPreferredSize();
        Container parent = getParent();
        if (parent == null || parent.getWidth() <= 0) {
            return naturalSize;
        }
        Insets parentInsets = parent.getInsets();
        int width = Math.max(1, parent.getWidth() - parentInsets.left - parentInsets.right);
        Object renderer = getClientProperty(BasicHTML.propertyKey);
        if (renderer instanceof View view) {
            Insets insets = getInsets();
            view.setSize(Math.max(1, width - insets.left - insets.right), 0);
            return new Dimension(Math.min(width, naturalSize.width),
                    (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom);
        }
        return naturalSize;
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);
        if (widthChanged) {
            // Nested BorderLayouts may measure before assigning their new width.
            // Request one more layout pass at the actual width after a resize.
            revalidate();
        }
    }
}
