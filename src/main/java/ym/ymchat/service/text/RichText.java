package ym.ymchat.service.text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class RichText {

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final Pattern AMPERSAND_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern MINI_HEX_OPEN = Pattern.compile("(?i)<#([0-9a-f]{6})>");
    private static final Pattern MINI_HEX_CLOSE = Pattern.compile("(?i)</#([0-9a-f]{6})>");
    private static final Pattern MINI_COLOR_OPEN = Pattern.compile("(?i)<color:#([0-9a-f]{6})>");
    private static final Pattern MINI_COLOR_CLOSE = Pattern.compile("(?i)</color>");
    private static final Pattern MINI_RESET = Pattern.compile("(?i)<reset>");

    private RichText() {
    }

    public static Component deserialize(String input) {
        return LEGACY_AMPERSAND.deserialize(normalize(input));
    }

    public static Style styleOf(String input) {
        if (input == null || input.isBlank()) {
            return Style.empty();
        }
        return deserialize(input + "x").style();
    }

    public static String serializeToSection(Component component) {
        return LEGACY_SECTION.serialize(component);
    }

    public static String toLegacySectionString(String input) {
        return serializeToSection(deserialize(input));
    }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return input == null ? "" : input;
        }

        String normalized = input.replace('§', '&');
        normalized = replaceAll(normalized, MINI_HEX_OPEN, RichText::hexMatch);
        normalized = replaceAll(normalized, MINI_COLOR_OPEN, RichText::hexMatch);
        normalized = replaceAll(normalized, AMPERSAND_HEX, RichText::hexMatch);
        normalized = MINI_HEX_CLOSE.matcher(normalized).replaceAll("&r");
        normalized = MINI_COLOR_CLOSE.matcher(normalized).replaceAll("&r");
        normalized = MINI_RESET.matcher(normalized).replaceAll("&r");
        return normalized;
    }

    private static String replaceAll(String input, Pattern pattern, java.util.function.Function<Matcher, String> replacer) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacer.apply(matcher)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static String hexMatch(Matcher matcher) {
        return toLegacyHex(matcher.group(1));
    }

    private static String toLegacyHex(String hex) {
        StringBuilder builder = new StringBuilder("&x");
        for (char digit : hex.toLowerCase(Locale.ROOT).toCharArray()) {
            builder.append('&').append(digit);
        }
        return builder.toString();
    }
}
