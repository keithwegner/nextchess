package com.github.keithwegner.chess.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class EvalBarPanel extends JPanel {
    private double whiteFraction = 0.5;
    private String label = "0.00";

    public EvalBarPanel() {
        setOpaque(true);
        setBackground(Color.WHITE);
    }

    public void setEval(double whiteFraction, String label) {
        this.whiteFraction = Math.max(0.0, Math.min(1.0, whiteFraction));
        this.label = label == null ? "0.00" : label;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int whiteHeight = (int) Math.round(h * whiteFraction);
        int blackHeight = h - whiteHeight;
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, w, blackHeight);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, blackHeight, w, whiteHeight);
        g2.setColor(new Color(0xC7CDD1));
        g2.drawRect(0, 0, w - 1, h - 1);

        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g2.getFontMetrics();
        int tx = Math.max(2, (w - fm.stringWidth(label)) / 2);

        g2.setColor(Color.WHITE);
        g2.drawString(label, tx, Math.min(h - 4, blackHeight + fm.getAscent() + 4));
        g2.setColor(Color.BLACK);
        g2.drawString(label, tx, Math.max(fm.getAscent() + 2, blackHeight - 6));
        g2.dispose();
    }
}
