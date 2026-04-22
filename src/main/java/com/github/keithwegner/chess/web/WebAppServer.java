package com.github.keithwegner.chess.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebAppServer {
    private final HttpServer server;
    private final ExecutorService executor;
    private final CountDownLatch stopLatch = new CountDownLatch(1);
    private final WebAppSession session = new WebAppSession();

    private WebAppServer(int port) throws IOException {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), Math.max(0, port));
        this.server = HttpServer.create(address, 0);
        this.executor = Executors.newCachedThreadPool();
        this.server.setExecutor(executor);
        registerContexts();
        this.server.start();
    }

    public static WebAppServer start(int port) throws IOException {
        return new WebAppServer(port);
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public WebAppSession session() {
        return session;
    }

    public void openInBrowser() throws IOException {
        URI uri = URI.create(baseUrl());
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Desktop browser launching is not supported in this environment.");
        }
        Desktop.getDesktop().browse(uri);
    }

    public void awaitStop() throws InterruptedException {
        stopLatch.await();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
        stopLatch.countDown();
    }

    private void registerContexts() {
        registerApi("/api/state", "GET", values -> session.snapshot());
        registerApi("/api/new-game", "POST", values -> session.newGame());
        registerApi("/api/clear-board", "POST", values -> session.clearBoard());
        registerApi("/api/load-fen", "POST", values -> session.loadFen(values.get("fen")));
        registerApi("/api/move", "POST", values -> session.move(values.get("uci")));
        registerApi("/api/undo", "POST", values -> session.undo());
        registerApi("/api/redo", "POST", values -> session.redo());
        registerApi("/api/analyze", "POST", session::analyze);
        registerApi("/api/engine/detect", "POST", values -> session.detectEngine());
        registerApi("/api/play-best", "POST", values -> session.playBestMove());
        registerApi("/api/setup/piece", "POST", values -> session.setupPiece(values.get("square"), values.get("pieceFen")));
        registerApi("/api/setup/metadata", "POST", session::setupMetadata);
        server.createContext("/", this::handleStatic);
    }

    private void registerApi(String path, String method, ApiAction action) {
        server.createContext(path, exchange -> {
            try {
                if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendMethodNotAllowed(exchange, method);
                    return;
                }
                Map<String, String> values = "POST".equalsIgnoreCase(method)
                        ? FormData.parse(exchange.getRequestBody())
                        : Map.of();
                WebAppSession.State state = action.apply(values);
                sendJson(exchange, 200, new ApiResponse(true, "", state));
            } catch (IllegalArgumentException | IllegalStateException ex) {
                sendJson(exchange, 400, new ApiResponse(false, ex.getMessage(), session.snapshot()));
            } catch (Exception ex) {
                sendJson(exchange, 500, new ApiResponse(false, "Unexpected server error: " + ex.getMessage(), session.snapshot()));
            } finally {
                exchange.close();
            }
        });
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String resource = switch (path) {
                case "/", "/index.html" -> "/webapp/index.html";
                case "/app.js" -> "/webapp/app.js";
                case "/styles.css" -> "/webapp/styles.css";
                default -> null;
            };
            if (resource == null) {
                sendText(exchange, 404, "text/plain; charset=utf-8", "Not found");
                return;
            }
            try (InputStream stream = WebAppServer.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    sendText(exchange, 404, "text/plain; charset=utf-8", "Not found");
                    return;
                }
                byte[] body = stream.readAllBytes();
                sendBytes(exchange, 200, contentTypeFor(resource), body);
            }
        } finally {
            exchange.close();
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        sendText(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
    }

    private void sendJson(HttpExchange exchange, int status, ApiResponse response) throws IOException {
        sendBytes(exchange, status, "application/json; charset=utf-8", JsonSupport.toJson(response).getBytes(StandardCharsets.UTF_8));
    }

    private void sendText(HttpExchange exchange, int status, String contentType, String text) throws IOException {
        sendBytes(exchange, status, contentType, text.getBytes(StandardCharsets.UTF_8));
    }

    private void sendBytes(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private String contentTypeFor(String resource) {
        if (resource.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (resource.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (resource.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private interface ApiAction {
        WebAppSession.State apply(Map<String, String> values) throws Exception;
    }

    private record ApiResponse(boolean ok, String error, WebAppSession.State state) {
    }
}
