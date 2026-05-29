package ym.ymchat.config.highlight;

import java.util.List;

public record PatternHighlightRule(
    String id,
    boolean enabled,
    int priority,
    List<String> channels,
    List<String> regexes,
    String color,
    List<String> formats,
    List<String> hover,
    String suggest,
    String command,
    String copy
) {

    public PatternHighlightRule {
        channels = channels == null ? List.of() : List.copyOf(channels);
        regexes = regexes == null ? List.of() : List.copyOf(regexes);
        formats = formats == null ? List.of() : List.copyOf(formats);
        hover = hover == null ? List.of() : List.copyOf(hover);
    }
}
