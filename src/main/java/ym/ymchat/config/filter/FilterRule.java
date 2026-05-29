package ym.ymchat.config.filter;

import java.util.List;
import java.util.Locale;

public record FilterRule(
    String id,
    String scope,
    String mode,
    String match,
    boolean regex,
    boolean caseSensitive,
    String replacement,
    String message,
    String bypassPermission,
    List<String> channels
) {

    public FilterRule {
        channels = channels == null ? List.of() : channels.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    public boolean appliesToScope(String currentScope) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return true;
        }
        return scope.equalsIgnoreCase(currentScope);
    }

    public boolean appliesToChannel(String channelId) {
        if (channels.isEmpty() || channels.contains("*") || channelId == null || channelId.isBlank()) {
            return true;
        }
        return channels.contains(channelId.toLowerCase(Locale.ROOT));
    }

    public boolean blockMode() {
        return "block".equalsIgnoreCase(mode);
    }
}
