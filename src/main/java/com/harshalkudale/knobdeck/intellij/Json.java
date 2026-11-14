package com.harshalkudale.knobdeck.intellij;

import java.util.List;
import java.util.Map;

/** Just enough JSON writing for the companion's small responses. */
final class Json {
  private Json() {}

  static String write(Object value) {
    StringBuilder sb = new StringBuilder();
    append(sb, value);
    return sb.toString();
  }

  private static void append(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String s) {
      string(sb, s);
    } else if (value instanceof Number || value instanceof Boolean) {
      sb.append(value);
    } else if (value instanceof Map<?, ?> map) {
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (!first) sb.append(',');
        first = false;
        string(sb, String.valueOf(e.getKey()));
        sb.append(':');
        append(sb, e.getValue());
      }
      sb.append('}');
    } else if (value instanceof List<?> list) {
      sb.append('[');
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) sb.append(',');
        append(sb, list.get(i));
      }
      sb.append(']');
    } else {
      string(sb, value.toString());
    }
  }

  private static void string(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
          else sb.append(c);
        }
      }
    }
    sb.append('"');
  }
}
