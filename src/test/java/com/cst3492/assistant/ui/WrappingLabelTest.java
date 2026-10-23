package com.cst3492.assistant.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

class WrappingLabelTest {
    @Test
    void narrowParentIncreasesLabelHeightWithoutIncreasingWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel parent = new JPanel(new BorderLayout());
            WrappingLabel label = new WrappingLabel(
                    "Question (Enter to send, Shift+Enter for a new line). " +
                            "Mock response received. You can send another question.");
            parent.add(label, BorderLayout.SOUTH);
            parent.setSize(500, 400);
            Dimension wide = label.getPreferredSize();
            parent.setSize(150, 400);
            Dimension narrow = label.getPreferredSize();
            assertTrue(narrow.height > wide.height);
            assertTrue(narrow.width <= 150);
            parent.doLayout();
            assertEquals(narrow.height, label.getHeight());
        });
    }

    @Test
    void statusUpdatesKeepPlainTextEscapedAndRecalculateHeight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel parent = new JPanel(new BorderLayout());
            parent.setSize(160, 400);
            WrappingLabel label = new WrappingLabel("Sending");
            parent.add(label, BorderLayout.SOUTH);
            int shortHeight = label.getPreferredSize().height;
            label.setText("<error> & please try again. " + "A longer status message. ".repeat(4));
            assertTrue(label.getText().contains("&lt;error&gt; &amp;"));
            assertTrue(label.getPreferredSize().height > shortHeight);
        });
    }
}
