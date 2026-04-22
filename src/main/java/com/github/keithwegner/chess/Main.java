package com.github.keithwegner.chess;

import com.github.keithwegner.chess.ui.NextChessFrame;
import com.github.keithwegner.chess.web.WebAppServer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        boolean swingMode = false;
        boolean openBrowser = true;
        int port = 0;

        for (String arg : args) {
            if ("--swing".equals(arg)) {
                swingMode = true;
            } else if ("--web".equals(arg)) {
                swingMode = false;
            } else if ("--no-browser".equals(arg)) {
                openBrowser = false;
            } else if (arg.startsWith("--port=")) {
                port = parsePort(arg.substring("--port=".length()));
            }
        }

        if (swingMode) {
            launchSwing();
            return;
        }

        launchWeb(port, openBrowser);
    }

    private static void launchSwing() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new NextChessFrame().setVisible(true));
    }

    private static void launchWeb(int port, boolean openBrowser) {
        try {
            WebAppServer server = WebAppServer.start(port);
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "next-chess-web-shutdown"));
            System.out.println("Next Chess is running at " + server.baseUrl());
            if (openBrowser) {
                try {
                    server.openInBrowser();
                } catch (IOException browserError) {
                    System.out.println("Open " + server.baseUrl() + " in your browser to use the app.");
                }
            }
            server.awaitStop();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to start the local web server.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535.");
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid port: " + text, ex);
        }
    }
}
