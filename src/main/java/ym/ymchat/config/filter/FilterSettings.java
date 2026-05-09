package ym.ymchat.config.filter;

import java.util.List;

public record FilterSettings(
    boolean enabled,
    List<FilterRule> rules
) {

    public static FilterSettings defaults() {
        return new FilterSettings(false, List.of());
    }
}
