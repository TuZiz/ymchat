package ym.ymchat.config.highlight;

import java.util.List;

public record KeywordHighlightRule(
    String id,
    boolean enabled,
    int priority,
    List<String> channels,
    String type,
    String match,
    List<String> matches,
    boolean caseSensitive,
    boolean wholeWord,
    String color,
    List<String> formats,
    List<String> hover,
    String suggest,
    String command,
    String copy
) {

    public KeywordHighlightRule {
        channels = channels == null ? List.of() : List.copyOf(channels);
        matches = matches == null ? List.of() : List.copyOf(matches);
        formats = formats == null ? List.of() : List.copyOf(formats);
        hover = hover == null ? List.of() : List.copyOf(hover);
    }

    public boolean regex() {
        return "regex".equalsIgnoreCase(type);
    }

    public List<String> effectiveMatches() {
        if (!matches.isEmpty()) {
            return matches;
        }
        return match == null || match.isBlank() ? List.of() : List.of(match);
    }
}
