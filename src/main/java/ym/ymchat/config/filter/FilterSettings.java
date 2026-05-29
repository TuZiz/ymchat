package ym.ymchat.config.filter;

import java.util.List;

public record FilterSettings(
    boolean enabled,
    FilterCloudSettings cloudSettings,
    List<FilterRule> rules
) {

    public FilterSettings {
        cloudSettings = cloudSettings == null ? FilterCloudSettings.defaults() : cloudSettings;
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static FilterSettings defaults() {
        return new FilterSettings(false, FilterCloudSettings.defaults(), List.of());
    }
}
