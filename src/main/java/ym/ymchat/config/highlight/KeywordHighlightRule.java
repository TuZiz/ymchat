package ym.ymchat.config.highlight;

import java.util.List;

public record KeywordHighlightRule(
    String id,
    boolean enabled,
    int priority,
    List<String> channels,
    String type,
    String match,
    boolean caseSensitive,
    boolean wholeWord,
    String color,
    List<String> formats
) {

    public KeywordHighlightRule {
        channels = channels == null ? List.of() : List.copyOf(channels);
        formats = formats == null ? List.of() : List.copyOf(formats);
    }

    public boolean regex() {
        return "regex".equalsIgnoreCase(type);
    }
}
