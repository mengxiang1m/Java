package com.cst3492.assistant.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/** Uncached vertical layout: wrapped rows are remeasured after viewport width changes. */
final class ChatColumnLayout implements LayoutManager {
    @Override public void addLayoutComponent(String name, Component component) { }
    @Override public void removeLayoutComponent(Component component) { }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        int height = insets.top + insets.bottom;
        int width = 0;
        for (Component component : parent.getComponents()) {
            if (component.isVisible()) {
                Dimension size = component.getPreferredSize();
                height += size.height;
                width = Math.max(width, size.width);
            }
        }
        return new Dimension(width + insets.left + insets.right, height);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }

    @Override
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int y = insets.top;
        int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
        for (Component component : parent.getComponents()) {
            if (component.isVisible()) {
                int height = component.getPreferredSize().height;
                component.setBounds(insets.left, y, width, height);
                y += height;
            }
        }
    }
}
