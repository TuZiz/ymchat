package ym.ymchat.config.highlight;

import java.util.List;

public record PublicChatHighlightSettings(
    boolean enabled,
    List<String> defaultChannels,
    List<KeywordHighlightRule> keywordRules,
    List<PatternHighlightRule> patternRules
) {

    public PublicChatHighlightSettings {
        defaultChannels = defaultChannels == null || defaultChannels.isEmpty()
            ? List.of("*")
            : List.copyOf(defaultChannels);
        keywordRules = keywordRules == null ? List.of() : List.copyOf(keywordRules);
        patternRules = patternRules == null ? List.of() : List.copyOf(patternRules);
    }

    public static PublicChatHighlightSettings defaults() {
        return new PublicChatHighlightSettings(
            true,
            List.of("*"),
            List.of(
                new KeywordHighlightRule(
                    "market-word",
                    true,
                    100,
                    List.of("*"),
                    "literal",
                    "sell",
                    List.of(),
                    false,
                    false,
                    "&#FFD166",
                    List.of("bold"),
                    List.of(),
                    "",
                    "",
                    ""
                ),
                new KeywordHighlightRule(
                    "urgent-word",
                    true,
                    90,
                    List.of("*"),
                    "regex",
                    "(urgent|help)",
                    List.of(),
                    false,
                    false,
                    "&#FF6B6B",
                    List.of("bold"),
                    List.of(),
                    "",
                    "",
                    ""
                )
            ),
            List.of(
                new PatternHighlightRule(
                    "price",
                    true,
                    80,
                    List.of("*"),
                    List.of(
                        "\\b\\d+(?:\\.\\d+)?[wkWK]\\b",
                        "\\b\\d+(?:\\.\\d+)?(coin|coins)\\b"
                    ),
                    "&#D4AF37",
                    List.of(),
                    List.of(),
                    "",
                    "",
                    ""
                ),
                new PatternHighlightRule(
                    "quantity",
                    true,
                    70,
                    List.of("*"),
                    List.of(
                        "(?i)x\\d+",
                        "\\*\\d+"
                    ),
                    "&#4ECDC4",
                    List.of(),
                    List.of(),
                    "",
                    "",
                    ""
                ),
                new PatternHighlightRule(
                    "coordinates",
                    true,
                    60,
                    List.of("*"),
                    List.of(
                        "-?\\d+\\s+-?\\d+\\s+-?\\d+",
                        "(?i)x\\s*:\\s*-?\\d+\\s*y\\s*:\\s*-?\\d+\\s*z\\s*:\\s*-?\\d+"
                    ),
                    "&#5DA9E9",
                    List.of("underlined"),
                    List.of(),
                    "",
                    "",
                    ""
                ),
                new PatternHighlightRule(
                    "time",
                    true,
                    50,
                    List.of("*"),
                    List.of(
                        "\\b\\d{1,2}:\\d{2}\\b",
                        "\\b\\d+[mhds]\\b"
                    ),
                    "&#C77DFF",
                    List.of(),
                    List.of(),
                    "",
                    "",
                    ""
                )
            )
        );
    }

    public boolean appliesToChannel(List<String> configuredChannels, String channelId) {
        List<String> candidates = configuredChannels == null || configuredChannels.isEmpty()
            ? defaultChannels
            : configuredChannels;
        if (candidates.isEmpty()) {
            return true;
        }
        String normalizedChannel = channelId == null ? "" : channelId.trim();
        for (String entry : candidates) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if ("*".equals(entry.trim()) || entry.trim().equalsIgnoreCase(normalizedChannel)) {
                return true;
            }
        }
        return false;
    }
}
