package ym.ymchat.service.color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import ym.ymchat.config.color.ColorPreset;
import ym.ymchat.service.text.RichText;

public final class ColorGradientUtil {

    private static final String NAME_COLOR_TOKEN = "{name_color}";
    private static final String NAME_RESET_TOKEN = "{name_reset}";

    private ColorGradientUtil() {
    }

    public static List<String> normalizeGradientColors(List<String> colors) {
        if (colors == null || colors.size() < 2) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String color : colors) {
            String value = ColorCodeUtil.normalizeGradientColorValue(color);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized.size() < 2 ? List.of() : List.copyOf(normalized);
    }

    public static String firstGradientColor(List<String> colors) {
        List<String> normalized = normalizeGradientColors(colors);
        return normalized.isEmpty() ? null : normalized.getFirst();
    }

    public static String colorAt(List<String> colors, int index, int totalLength) {
        List<String> normalized = normalizeGradientColors(colors);
        return colorAtNormalized(normalized, index, totalLength);
    }

    private static String colorAtNormalized(List<String> normalized, int index, int totalLength) {
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() == 1 || totalLength <= 1) {
            return normalized.getFirst();
        }

        int safeIndex = Math.max(0, Math.min(index, totalLength - 1));
        double position = (double) safeIndex / (double) (totalLength - 1);
        double scaled = position * (normalized.size() - 1);
        int segment = Math.min((int) Math.floor(scaled), normalized.size() - 2);
        double local = scaled - segment;
        int[] from = rgb(normalized.get(segment));
        int[] to = rgb(normalized.get(segment + 1));
        int red = interpolate(from[0], to[0], local);
        int green = interpolate(from[1], to[1], local);
        int blue = interpolate(from[2], to[2], local);
        return String.format(Locale.ROOT, "&#%02X%02X%02X", red, green, blue);
    }

    public static String applyGradientCodes(String text, List<String> colors) {
        String input = text == null ? "" : text;
        List<String> normalized = normalizeGradientColors(colors);
        if (input.isEmpty() || normalized.isEmpty()) {
            return input;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            builder.append(colorAtNormalized(normalized, index, input.length())).append(input.charAt(index));
        }
        return builder.toString();
    }

    public static String applyNameColorTokens(
        String input,
        PlayerColorService.ResolvedColor resolvedColor,
        String fallbackColor
    ) {
        if (input == null || (!input.contains(NAME_COLOR_TOKEN) && !input.contains(NAME_RESET_TOKEN))) {
            return input;
        }
        String baseColor = resolvedColor == null || resolvedColor.baseColorValue() == null || resolvedColor.baseColorValue().isBlank()
            ? fallbackColor
            : resolvedColor.baseColorValue();
        String normalizedBase = ColorCodeUtil.normalizeBaseColorValue(baseColor);
        if (normalizedBase == null) {
            normalizedBase = "&f";
        }
        List<String> gradient = resolvedColor == null || resolvedColor.rgbColor() == null
            ? List.of()
            : normalizeGradientColors(resolvedColor.rgbColor().gradientColors());

        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        boolean active = false;
        while (cursor < input.length()) {
            int colorIndex = input.indexOf(NAME_COLOR_TOKEN, cursor);
            int resetIndex = input.indexOf(NAME_RESET_TOKEN, cursor);
            int nextIndex = nextTokenIndex(colorIndex, resetIndex);
            if (nextIndex < 0) {
                appendNameSegment(builder, input.substring(cursor), active, normalizedBase, gradient);
                break;
            }
            appendNameSegment(builder, input.substring(cursor, nextIndex), active, normalizedBase, gradient);
            if (nextIndex == colorIndex) {
                active = true;
                cursor = nextIndex + NAME_COLOR_TOKEN.length();
            } else {
                if (active) {
                    builder.append("&r");
                }
                active = false;
                cursor = nextIndex + NAME_RESET_TOKEN.length();
            }
        }
        return builder.toString();
    }

    public static Component component(
        String text,
        List<String> colors,
        int gradientStart,
        int gradientLength,
        String formatCodes,
        Component hover,
        ClickEvent click
    ) {
        String input = text == null ? "" : text;
        List<String> normalized = normalizeGradientColors(colors);
        if (input.isEmpty()) {
            return Component.empty();
        }
        if (normalized.isEmpty()) {
            return attach(Component.text(input).style(RichText.styleOf(formatCodes)), hover, click);
        }

        int totalLength = Math.max(gradientLength, input.length());
        TextComponent.Builder builder = Component.text();
        for (int offset = 0; offset < input.length(); offset++) {
            String color = colorAtNormalized(normalized, gradientStart + offset, totalLength);
            builder.append(attach(
                Component.text(String.valueOf(input.charAt(offset))).style(RichText.styleOf(color + safe(formatCodes))),
                hover,
                click
            ));
        }
        return builder.build();
    }

    public static List<String> gradientColors(PlayerColorService.ResolvedColor resolvedColor) {
        ColorPreset preset = resolvedColor == null ? null : resolvedColor.rgbColor();
        return preset == null ? List.of() : normalizeGradientColors(preset.gradientColors());
    }

    private static void appendNameSegment(
        StringBuilder builder,
        String segment,
        boolean active,
        String baseColor,
        List<String> gradient
    ) {
        if (segment == null || segment.isEmpty()) {
            return;
        }
        if (!active) {
            builder.append(segment);
            return;
        }
        if (gradient.isEmpty()) {
            builder.append(baseColor).append(segment);
            return;
        }
        builder.append(applyGradientCodes(segment, gradient));
    }

    private static int nextTokenIndex(int colorIndex, int resetIndex) {
        if (colorIndex < 0) {
            return resetIndex;
        }
        if (resetIndex < 0) {
            return colorIndex;
        }
        return Math.min(colorIndex, resetIndex);
    }

    private static Component attach(Component component, Component hover, ClickEvent click) {
        Component result = component;
        if (hover != null) {
            result = result.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover));
        }
        if (click != null) {
            result = result.clickEvent(click);
        }
        return result;
    }

    private static int[] rgb(String color) {
        String hex = color.substring(2);
        return new int[]{
            Integer.parseInt(hex.substring(0, 2), 16),
            Integer.parseInt(hex.substring(2, 4), 16),
            Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private static int interpolate(int from, int to, double position) {
        return (int) Math.round(from + ((to - from) * position));
    }

    private static String safe(String input) {
        return input == null ? "" : input;
    }
}
