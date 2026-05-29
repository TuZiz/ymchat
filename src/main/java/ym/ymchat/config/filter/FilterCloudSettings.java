package ym.ymchat.config.filter;

import java.util.List;

public record FilterCloudSettings(
    boolean enabled,
    String url,
    String arrayPath,
    long refreshMinutes,
    String mode,
    String replacement,
    String message,
    String bypassPermission,
    List<String> scopes
) {

    public FilterCloudSettings {
        scopes = scopes == null ? List.of("all") : List.copyOf(scopes);
    }

    public static FilterCloudSettings defaults() {
        return new FilterCloudSettings(
            false,
            "",
            "words",
            60L,
            "block",
            "***",
            "lang:filter.default-blocked-message",
            "ymchat.filter.bypass",
            List.of("all")
        );
    }
}
