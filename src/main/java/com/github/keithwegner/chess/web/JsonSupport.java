package com.github.keithwegner.chess.web;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.Map;

final class JsonSupport {
    private JsonSupport() {
    }

    static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value);
        return sb.toString();
    }

    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String text) {
            appendString(sb, text);
            return;
        }
        if (value instanceof Character character) {
            appendString(sb, String.valueOf(character));
            return;
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            sb.append(value);
            return;
        }
        if (value instanceof Float floatValue) {
            sb.append(Float.isFinite(floatValue) ? floatValue : "null");
            return;
        }
        if (value instanceof Double doubleValue) {
            sb.append(Double.isFinite(doubleValue) ? doubleValue : "null");
            return;
        }
        if (value instanceof Enum<?> enumValue) {
            appendString(sb, enumValue.name());
            return;
        }
        if (value instanceof Map<?, ?> map) {
            appendObject(sb, map);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            appendArray(sb, iterable.iterator());
            return;
        }
        if (value.getClass().isArray()) {
            appendArray(sb, value);
            return;
        }
        if (value.getClass().isRecord()) {
            appendRecord(sb, value);
            return;
        }
        appendString(sb, value.toString());
    }

    private static void appendObject(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, key);
            sb.append(':');
            appendJson(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void appendArray(StringBuilder sb, Iterator<?> iterator) {
        sb.append('[');
        boolean first = true;
        while (iterator.hasNext()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendJson(sb, iterator.next());
        }
        sb.append(']');
    }

    private static void appendArray(StringBuilder sb, Object array) {
        sb.append('[');
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendJson(sb, Array.get(array, i));
        }
        sb.append(']');
    }

    private static void appendRecord(StringBuilder sb, Object record) {
        sb.append('{');
        boolean first = true;
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, component.getName());
            sb.append(':');
            try {
                appendJson(sb, component.getAccessor().invoke(record));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Unable to serialize record component " + component.getName(), ex);
            }
        }
        sb.append('}');
    }

    private static void appendString(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
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
    }
}
