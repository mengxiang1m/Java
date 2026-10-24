package com.cst3492.assistant.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

class WrappingLabelTest {
    @Test
    void centerLabelAccountsForAdjacentContextActionAtNarrowWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel parent = new JPanel(new BorderLayout());
            parent.setSize(210, 200);
            WrappingLabel label = new WrappingLabel("Attached Java code");
            javax.swing.JButton clear = new javax.swing.JButton("Clear context");
            parent.add(label, BorderLayout.CENTER);
            parent.add(clear, BorderLayout.EAST);
            int available = parent.getWidth() - clear.getPreferredSize().width;
            assertTrue(label.getPreferredSize().width <= available);
            assertTrue(label.getPreferredSize().height > label.getFont().getSize());
            parent.setSize(parent.getWidth(), parent.getPreferredSize().height);
            parent.doLayout();
            assertTrue(label.getHeight() >= label.getPreferredSize().height);
        });
    }

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
