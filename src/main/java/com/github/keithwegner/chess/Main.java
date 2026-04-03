package com.github.keithwegner.chess;

import com.github.keithwegner.chess.ui.NextChessFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new NextChessFrame().setVisible(true));
    }
}
