package com.github.keithwegner.chess.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class JsonSupportTest {
    private record Nested(boolean ready) {
    }

    private record Sample(String text, int count, Nested nested) {
    }

    @Test
    void serializesRecordsAndEscapesStrings() {
        String json = JsonSupport.toJson(new Sample("line\n\"quote\"", 2, new Nested(true)));

        assertEquals("{\"text\":\"line\\n\\\"quote\\\"\",\"count\":2,\"nested\":{\"ready\":true}}", json);
    }
}
