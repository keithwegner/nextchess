package com.github.keithwegner.chess.web;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrowserUiSmokeTest {
    private static final Duration BROWSER_TIMEOUT = Duration.ofSeconds(12);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Test
    void rendersBoardWithoutHorizontalOverflowAcrossViewports() throws Exception {
        Path chrome = requireChrome();
        WebAppServer server = WebAppServer.start(0);
        try {
            String desktopMetrics = withBrowser(chrome, server.baseUrl(), 1440, 1000, "desktop", client -> {
                waitForBoard(client);
                captureScreenshot(client, "desktop");
                return client.evaluate("""
                        (() => {
                            const board = document.querySelector(".board-frame");
                            const rect = board.getBoundingClientRect();
                            return JSON.stringify({
                                squares: document.querySelectorAll(".square").length,
                                pieces: document.querySelectorAll(".square__piece").length,
                                boardWidth: Math.round(rect.width),
                                viewport: document.documentElement.clientWidth,
                                overflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
                                boardFits: rect.left >= -1 && rect.right <= document.documentElement.clientWidth + 1
                            });
                        })()
                        """);
            });
            assertContains(desktopMetrics, "\"squares\":64");
            assertContains(desktopMetrics, "\"pieces\":32");
            assertContains(desktopMetrics, "\"overflowX\":false");
            assertContains(desktopMetrics, "\"boardFits\":true");

            String mobileMetrics = withBrowser(chrome, server.baseUrl(), 390, 900, "mobile", client -> {
                waitForBoard(client);
                captureScreenshot(client, "mobile");
                return client.evaluate("""
                        (() => {
                            const board = document.querySelector(".board-frame");
                            const rect = board.getBoundingClientRect();
                            return JSON.stringify({
                                squares: document.querySelectorAll(".square").length,
                                pieces: document.querySelectorAll(".square__piece").length,
                                boardWidth: Math.round(rect.width),
                                viewport: document.documentElement.clientWidth,
                                overflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
                                boardFits: rect.left >= -1 && rect.right <= document.documentElement.clientWidth + 1
                            });
                        })()
                        """);
            });
            assertContains(mobileMetrics, "\"squares\":64");
            assertContains(mobileMetrics, "\"pieces\":32");
            assertContains(mobileMetrics, "\"overflowX\":false");
            assertContains(mobileMetrics, "\"boardFits\":true");
        } finally {
            server.stop();
        }
    }

    @Test
    void playSetupAndPromotionWorkThroughBrowserInteractions() throws Exception {
        Path chrome = requireChrome();
        WebAppServer server = WebAppServer.start(0);
        try {
            String flow = withBrowser(chrome, server.baseUrl(), 1280, 900, "flow", client -> {
                waitForBoard(client);
                return client.evaluate("""
                        (async () => {
                            const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
                            const apiState = () => fetch("/api/state").then(response => response.json()).then(payload => payload.state);
                            const waitForIdle = async () => {
                                for (let i = 0; i < 30; i++) {
                                    if (!document.body.classList.contains("is-busy")) {
                                        return true;
                                    }
                                    await delay(100);
                                }
                                return false;
                            };
                            const waitForState = async predicate => {
                                for (let i = 0; i < 30; i++) {
                                    const next = await apiState();
                                    if (predicate(next)) {
                                        return next;
                                    }
                                    await delay(100);
                                }
                                return apiState();
                            };

                            document.querySelector("[data-square='e2']").click();
                            await delay(100);
                            const selected = !!document.querySelector(".square.is-selected[data-square='e2']");
                            const targetCount = document.querySelectorAll(".square__hint.is-target").length;
                            document.querySelector("[data-square='e4']").click();
                            const afterMove = await waitForState(state => state.sideToMove === "BLACK");
                            await waitForIdle();

                            document.getElementById("setup-mode-button").click();
                            await fetch("/api/clear-board", {method: "POST"});
                            await refreshState();
                            await waitForIdle();
                            await delay(100);
                            document.querySelector("[data-piece='K']").click();
                            document.querySelector("[data-square='e1']").click();
                            await waitForState(state => state.board.some(square => square.square === "e1" && square.pieceFen === "K"));
                            await waitForIdle();
                            document.querySelector("[data-piece='k']").click();
                            document.querySelector("[data-square='e8']").click();
                            const afterSetup = await waitForState(state => state.analyzable);
                            await waitForIdle();
                            document.querySelector("[data-erase='true']").click();
                            document.querySelector("[data-square='e1']").click();
                            const afterErase = await waitForState(state => state.board.some(square => square.square === "e1" && square.pieceFen === ""));
                            await waitForIdle();

                            const fen = "4k3/P7/8/8/8/8/8/7K w - - 0 1";
                            await fetch("/api/load-fen", {
                                method: "POST",
                                headers: {"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"},
                                body: new URLSearchParams({fen}).toString()
                            });
                            await refreshState();
                            document.getElementById("play-mode-button").click();
                            document.querySelector("[data-square='a7']").click();
                            await delay(100);
                            document.querySelector("[data-square='a8']").click();
                            await delay(100);
                            const promotionOpen = !document.getElementById("promotion-modal").hidden;
                            const promotionOptions = document.querySelectorAll(".promotion-option").length;
                            document.getElementById("promotion-cancel-button").click();

                            return JSON.stringify({
                                selected,
                                targetCount,
                                afterMoveSide: afterMove.sideToMove,
                                afterMoveFen: afterMove.fen,
                                setupFen: afterSetup.fen,
                                setupAnalyzable: afterSetup.analyzable,
                                erased: afterErase.board.find(square => square.square === "e1").pieceFen === "",
                                promotionOpen,
                                promotionOptions
                            });
                        })()
                        """);
            });

            assertContains(flow, "\"selected\":true");
            assertFalse(flow.contains("\"targetCount\":0"), flow);
            assertContains(flow, "\"afterMoveSide\":\"BLACK\"");
            assertContains(flow, "\"setupAnalyzable\":true");
            assertContains(flow, "\"erased\":true");
            assertContains(flow, "\"promotionOpen\":true");
            assertContains(flow, "\"promotionOptions\":4");
        } finally {
            server.stop();
        }
    }

    private static void waitForBoard(CdpClient client) throws Exception {
        long deadline = System.nanoTime() + BROWSER_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            String metrics = client.evaluate("""
                    (() => JSON.stringify({
                        ready: document.readyState,
                        squares: document.querySelectorAll(".square").length,
                        pieces: document.querySelectorAll(".square__piece").length
                    }))()
                    """);
            if (metrics.contains("\"squares\":64") && metrics.contains("\"pieces\":32")) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Browser board did not finish rendering.");
    }

    private static String withBrowser(Path chrome, String url, int width, int height, String name, BrowserAction action)
            throws Exception {
        Path userDataDir = Files.createTempDirectory("next-chess-chrome-" + name);
        Process process = null;
        try {
            List<String> command = new ArrayList<>();
            command.add(chrome.toString());
            command.add("--headless=new");
            command.add("--disable-gpu");
            command.add("--disable-background-networking");
            command.add("--disable-component-update");
            command.add("--disable-dev-shm-usage");
            command.add("--no-first-run");
            command.add("--no-sandbox");
            command.add("--remote-debugging-port=0");
            command.add("--user-data-dir=" + userDataDir);
            command.add("--window-size=" + width + "," + height);
            command.add(url);
            process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            int port = waitForDebugPort(userDataDir);
            String websocketUrl = waitForPageWebsocket(port, url);
            try (CdpClient client = CdpClient.connect(websocketUrl)) {
                client.send("Page.enable", "{}");
                client.send("Runtime.enable", "{}");
                return action.run(client);
            }
        } finally {
            if (process != null) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            }
            deleteRecursively(userDataDir);
        }
    }

    private static int waitForDebugPort(Path userDataDir) throws Exception {
        Path activePort = userDataDir.resolve("DevToolsActivePort");
        long deadline = System.nanoTime() + BROWSER_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(activePort)) {
                List<String> lines = Files.readAllLines(activePort);
                if (!lines.isEmpty() && !lines.get(0).isBlank()) {
                    return Integer.parseInt(lines.get(0).trim());
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Chrome did not expose a DevTools port.");
    }

    private static String waitForPageWebsocket(int port, String expectedUrl) throws Exception {
        long deadline = System.nanoTime() + BROWSER_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            String body = httpGet("http://127.0.0.1:" + port + "/json/list");
            String websocketUrl = firstWebsocketUrlFor(body, expectedUrl).orElse("");
            if (!websocketUrl.isBlank()) {
                return websocketUrl;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Unable to find Chrome page target for " + expectedUrl);
    }

    private static Optional<String> firstWebsocketUrlFor(String json, String expectedUrl) {
        int index = 0;
        while (index >= 0 && index < json.length()) {
            int urlKey = json.indexOf("\"url\"", index);
            if (urlKey < 0) {
                return Optional.empty();
            }
            int urlStart = json.indexOf('"', json.indexOf(':', urlKey) + 1);
            String url = readJsonString(json, urlStart);
            int wsKey = json.indexOf("\"webSocketDebuggerUrl\"", urlStart);
            if (wsKey < 0) {
                return Optional.empty();
            }
            int wsStart = json.indexOf('"', json.indexOf(':', wsKey) + 1);
            String websocketUrl = readJsonString(json, wsStart);
            if (url.startsWith(expectedUrl)) {
                return Optional.of(websocketUrl);
            }
            index = wsStart + 1;
        }
        return Optional.empty();
    }

    private static String httpGet(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static void captureScreenshot(CdpClient client, String name) throws Exception {
        String response = client.send("Page.captureScreenshot", "{\"format\":\"png\",\"captureBeyondViewport\":false}");
        String data = extractStringField(response, "data");
        Path output = Path.of("target", "browser-smoke", name + ".png");
        Files.createDirectories(output.getParent());
        Files.write(output, Base64.getDecoder().decode(data));
    }

    private static Path requireChrome() {
        Optional<Path> chrome = findChrome();
        Assumptions.assumeTrue(chrome.isPresent(), "Headless Chrome is not available.");
        return chrome.orElseThrow();
    }

    private static Optional<Path> findChrome() {
        String chromeBin = System.getenv("CHROME_BIN");
        if (chromeBin != null && !chromeBin.isBlank()) {
            Path path = Path.of(chromeBin);
            if (Files.isExecutable(path)) {
                return Optional.of(path);
            }
        }

        List<String> names = operatingSystem().contains("win")
                ? List.of("chrome.exe", "msedge.exe")
                : List.of("google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "chrome");
        for (String name : names) {
            Optional<Path> path = findOnPath(name);
            if (path.isPresent()) {
                return path;
            }
        }

        List<Path> candidates = new ArrayList<>();
        if (operatingSystem().contains("mac")) {
            candidates.add(Path.of("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
            candidates.add(Path.of("/Applications/Chromium.app/Contents/MacOS/Chromium"));
            candidates.add(Path.of("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"));
        } else if (operatingSystem().contains("win")) {
            String programFiles = System.getenv("ProgramFiles");
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            String localAppData = System.getenv("LOCALAPPDATA");
            addWindowsChrome(candidates, programFiles);
            addWindowsChrome(candidates, programFilesX86);
            if (localAppData != null) {
                candidates.add(Path.of(localAppData, "Google", "Chrome", "Application", "chrome.exe"));
                candidates.add(Path.of(localAppData, "Microsoft", "Edge", "Application", "msedge.exe"));
            }
        }

        return candidates.stream().filter(Files::isExecutable).findFirst();
    }

    private static void addWindowsChrome(List<Path> candidates, String root) {
        if (root == null || root.isBlank()) {
            return;
        }
        candidates.add(Path.of(root, "Google", "Chrome", "Application", "chrome.exe"));
        candidates.add(Path.of(root, "Microsoft", "Edge", "Application", "msedge.exe"));
    }

    private static Optional<Path> findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        for (String entry : path.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(entry, executable);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static String operatingSystem() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    private static void assertContains(String text, String expected) {
        assertTrue(text.contains(expected), () -> "Expected " + expected + " in " + text);
    }

    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String extractStringField(String json, String fieldName) {
        int key = json.indexOf("\"" + fieldName + "\"");
        if (key < 0) {
            throw new IllegalArgumentException("Missing field " + fieldName + " in " + json);
        }
        int start = json.indexOf('"', json.indexOf(':', key) + 1);
        if (start < 0) {
            throw new IllegalArgumentException("Field " + fieldName + " is not a string in " + json);
        }
        return readJsonString(json, start);
    }

    private static String readJsonString(String json, int quoteIndex) {
        if (quoteIndex < 0 || quoteIndex >= json.length() || json.charAt(quoteIndex) != '"') {
            throw new IllegalArgumentException("Expected JSON string at " + quoteIndex + " in " + json);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = quoteIndex + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                return sb.toString();
            }
            if (ch == '\\') {
                if (i + 1 >= json.length()) {
                    throw new IllegalArgumentException("Unterminated escape in " + json);
                }
                char escaped = json.charAt(++i);
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 >= json.length()) {
                            throw new IllegalArgumentException("Bad unicode escape in " + json);
                        }
                        String hex = json.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad escape \\" + escaped + " in " + json);
                }
            } else {
                sb.append(ch);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string in " + json);
    }

    private static void deleteRecursively(Path path) {
        if (path == null) {
            return;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            if (!Files.exists(path)) {
                return;
            }
            try {
                deleteRecursivelyOnce(path);
                return;
            } catch (IOException ignored) {
                pauseBeforeRetry();
            }
        }
    }

    private static void deleteRecursivelyOnce(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface BrowserAction {
        String run(CdpClient client) throws Exception;
    }

    private static final class CdpClient implements WebSocket.Listener, AutoCloseable {
        private final AtomicInteger nextId = new AtomicInteger();
        private final ConcurrentHashMap<Integer, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket webSocket;

        static CdpClient connect(String websocketUrl) throws Exception {
            CdpClient client = new CdpClient();
            client.webSocket = HTTP.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .buildAsync(URI.create(websocketUrl), client)
                    .get(3, TimeUnit.SECONDS);
            return client;
        }

        String evaluate(String expression) throws Exception {
            String response = send("Runtime.evaluate",
                    "{\"expression\":" + jsonString(expression)
                            + ",\"awaitPromise\":true,\"returnByValue\":true,\"timeout\":5000}");
            return extractStringField(response, "value");
        }

        String send(String method, String params) throws Exception {
            int id = nextId.incrementAndGet();
            CompletableFuture<String> future = new CompletableFuture<>();
            pending.put(id, future);
            String message = "{\"id\":" + id + ",\"method\":" + jsonString(method) + ",\"params\":" + params + "}";
            webSocket.sendText(message, true).get(3, TimeUnit.SECONDS);
            String response = future.get(BROWSER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (response.contains("\"error\"")) {
                throw new IllegalStateException(response);
            }
            return response;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String message = partial.toString();
                partial.setLength(0);
                int id = extractId(message);
                if (id > 0) {
                    CompletableFuture<String> future = pending.remove(id);
                    if (future != null) {
                        future.complete(message);
                    }
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        private int extractId(String message) {
            int key = message.indexOf("\"id\"");
            if (key < 0) {
                return -1;
            }
            int colon = message.indexOf(':', key);
            int end = colon + 1;
            while (end < message.length() && Character.isDigit(message.charAt(end))) {
                end++;
            }
            if (end == colon + 1) {
                return -1;
            }
            return Integer.parseInt(message.substring(colon + 1, end));
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public void close() {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            }
        }
    }
}
