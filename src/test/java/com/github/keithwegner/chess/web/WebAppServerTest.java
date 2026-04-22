package com.github.keithwegner.chess.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WebAppServerTest {
    @Test
    void servesStaticAppAndApi() throws IOException, InterruptedException {
        WebAppServer server = WebAppServer.start(0);
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> indexResponse = client.send(
                    HttpRequest.newBuilder(URI.create(server.baseUrl() + "/"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(indexResponse.body().contains("Next Chess"));

            HttpResponse<String> stateResponse = client.send(
                    HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/state"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(stateResponse.body().contains("\"ok\":true"));
            assertTrue(stateResponse.body().contains("\"fen\":\"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\""));

            HttpResponse<String> moveResponse = client.send(
                    HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/move"))
                            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString("uci=e2e4"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(moveResponse.body().contains("\"lastMoveUci\":\"e2e4\""));

            HttpResponse<String> clearResponse = post(client, server, "/api/clear-board", "");
            assertEquals(200, clearResponse.statusCode());
            assertTrue(clearResponse.body().contains("\"fen\":\"8/8/8/8/8/8/8/8 w - - 0 1\""));
            assertTrue(clearResponse.body().contains("\"metadata\":"));

            HttpResponse<String> whiteKingResponse = post(client, server, "/api/setup/piece", "square=e1&pieceFen=K");
            assertEquals(200, whiteKingResponse.statusCode());
            assertTrue(whiteKingResponse.body().contains("\"square\":\"e1\",\"pieceFen\":\"K\""));

            HttpResponse<String> blackKingResponse = post(client, server, "/api/setup/piece", "square=e8&pieceFen=k");
            assertEquals(200, blackKingResponse.statusCode());
            assertTrue(blackKingResponse.body().contains("\"square\":\"e8\",\"pieceFen\":\"k\""));

            HttpResponse<String> metadataResponse = post(client, server, "/api/setup/metadata",
                    "sideToMove=BLACK&whiteCastleKing=false&whiteCastleQueen=false&blackCastleKing=false"
                            + "&blackCastleQueen=false&enPassantSquare=-&halfmoveClock=3&fullmoveNumber=9");
            assertEquals(200, metadataResponse.statusCode());
            assertTrue(metadataResponse.body().contains("\"sideToMove\":\"BLACK\""));
            assertTrue(metadataResponse.body().contains("\"halfmoveClock\":3"));
            assertTrue(metadataResponse.body().contains("\"fullmoveNumber\":9"));

            HttpResponse<String> badSetupResponse = post(client, server, "/api/setup/piece", "square=z9&pieceFen=Q");
            assertEquals(400, badSetupResponse.statusCode());
            assertTrue(badSetupResponse.body().contains("\"ok\":false"));
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> post(HttpClient client, WebAppServer server, String path, String body)
            throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
                        .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
