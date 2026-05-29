package ym.ymchat.service.color;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import ym.ymchat.service.text.RichText;
public final class PublicChatColorService {

    public PreparedPublicChatMessage prepare(
        String rawMessage,
        String defaultColor,
        PlayerColorService.ResolvedColor resolvedColor,
        PermissionAccess permissions
    ) {
        String baseColorValue = ColorCodeUtil.normalizeBaseColorValue(
            resolvedColor == null ? defaultColor : resolvedColor.baseColorValue()
        );
        if (baseColorValue == null) {
            baseColorValue = ColorCodeUtil.normalizeBaseColorValue(defaultColor);
        }
        if (baseColorValue == null) {
            baseColorValue = "&f";
        }
        List<String> baseGradientColors = ColorGradientUtil.gradientColors(resolvedColor);

        FormattingState state = new FormattingState(baseColorValue, baseGradientColors);
        List<TextSpan> spans = new ArrayList<>();
        StringBuilder visible = new StringBuilder();
        StringBuilder formatted = new StringBuilder();
        StringBuilder currentText = new StringBuilder();
        boolean usedInlineFormatting = false;
        boolean hadUnauthorizedFormatting = false;

        String input = rawMessage == null ? "" : rawMessage;
        int index = 0;
        while (index < input.length()) {
            String rgbAmpersand = matchAmpersandRgb(input, index);
            if (rgbAmpersand != null) {
                if (permissions.rgbAllowed()) {
                    flush(spans, currentText, state, visible.length());
                    state.applyColor("&#" + rgbAmpersand);
                    formatted.append("&#").append(rgbAmpersand);
                    usedInlineFormatting = true;
                } else {
                    String literal = input.substring(index, index + 8);
                    appendLiteral(currentText, visible, formatted, literal);
                    hadUnauthorizedFormatting = true;
                }
                index += 8;
                continue;
            }

            String rgbMini = matchMiniRgb(input, index);
            if (rgbMini != null) {
                if (permissions.rgbAllowed()) {
                    flush(spans, currentText, state, visible.length());
                    state.applyColor("&#" + rgbMini);
                    formatted.append("&#").append(rgbMini);
                    usedInlineFormatting = true;
                } else {
                    String literal = input.substring(index, index + 9);
                    appendLiteral(currentText, visible, formatted, literal);
                    hadUnauthorizedFormatting = true;
                }
                index += 9;
                continue;
            }

            String colorTagRgb = matchColorTagRgb(input, index);
            if (colorTagRgb != null) {
                if (permissions.rgbAllowed()) {
                    flush(spans, currentText, state, visible.length());
                    state.applyColor("&#" + colorTagRgb);
                    formatted.append("&#").append(colorTagRgb);
                    usedInlineFormatting = true;
                } else {
                    String literal = input.substring(index, index + 15);
                    appendLiteral(currentText, visible, formatted, literal);
                    hadUnauthorizedFormatting = true;
                }
                index += 15;
                continue;
            }

            if (matchesIgnoreCase(input, index, "</color>")) {
                if (permissions.rgbAllowed()) {
                    flush(spans, currentText, state, visible.length());
                    state.reset();
                    formatted.append("&r");
                    usedInlineFormatting = true;
                } else {
                    appendLiteral(currentText, visible, formatted, "</color>");
                    hadUnauthorizedFormatting = true;
                }
                index += 8;
                continue;
            }

            if (matchesIgnoreCase(input, index, "<reset>")) {
                if (permissions.rgbAllowed()) {
                    flush(spans, currentText, state, visible.length());
                    state.reset();
                    formatted.append("&r");
                    usedInlineFormatting = true;
                } else {
                    appendLiteral(currentText, visible, formatted, "<reset>");
                    hadUnauthorizedFormatting = true;
                }
                index += 7;
                continue;
            }

            String closeMiniRgb = matchCloseMiniRgb(input, index);
            if (closeMiniRgb != null) {
                if (permissions.rgbAllowed()) {
                    flush(spans, currentText, state, visible.length());
                    state.reset();
                    formatted.append("&r");
                    usedInlineFormatting = true;
                } else {
                    String literal = input.substring(index, index + 10);
                    appendLiteral(currentText, visible, formatted, literal);
                    hadUnauthorizedFormatting = true;
                }
                index += 10;
                continue;
            }

            if (input.charAt(index) == '&' && index + 1 < input.length()) {
                char code = input.charAt(index + 1);
                if (ColorCodeUtil.isLegacyColorCode(code)) {
                    if (permissions.legacyAllowed()) {
                        flush(spans, currentText, state, visible.length());
                        if (Character.toLowerCase(code) == 'r') {
                            state.reset();
                        } else {
                            state.applyColor("&" + Character.toLowerCase(code));
                        }
                        formatted.append('&').append(Character.toLowerCase(code));
                        usedInlineFormatting = true;
                    } else {
                        appendLiteral(currentText, visible, formatted, input.substring(index, index + 2));
                        hadUnauthorizedFormatting = true;
                    }
                    index += 2;
                    continue;
                }

                if (ColorCodeUtil.isLegacyFormatCode(code)) {
                    if (permissions.formatAllowed()) {
                        flush(spans, currentText, state, visible.length());
                        state.applyFormat(Character.toLowerCase(code));
                        formatted.append('&').append(Character.toLowerCase(code));
                        usedInlineFormatting = true;
                    } else {
                        appendLiteral(currentText, visible, formatted, input.substring(index, index + 2));
                        hadUnauthorizedFormatting = true;
                    }
                    index += 2;
                    continue;
                }
            }

            char character = input.charAt(index);
            currentText.append(character);
            visible.append(character);
            formatted.append(character);
            index++;
        }

        flush(spans, currentText, state, visible.length());
        List<TextSpan> finalSpans = spans.stream()
            .map(span -> span.withGradientLength(visible.length()))
            .toList();
        return new PreparedPublicChatMessage(
            visible.toString(),
            formatted.toString(),
            baseColorValue,
            usedInlineFormatting,
            hadUnauthorizedFormatting,
            finalSpans
        );
    }

    public PreparedPublicChatMessage plain(String visiblePlainText, String baseColorValue) {
        return plain(visiblePlainText, baseColorValue, List.of());
    }

    public PreparedPublicChatMessage plain(
        String visiblePlainText,
        PlayerColorService.ResolvedColor resolvedColor,
        String defaultColor
    ) {
        String baseColorValue = resolvedColor == null ? defaultColor : resolvedColor.baseColorValue();
        return plain(visiblePlainText, baseColorValue, ColorGradientUtil.gradientColors(resolvedColor));
    }

    private PreparedPublicChatMessage plain(String visiblePlainText, String baseColorValue, List<String> gradientColors) {
        String normalizedBaseColor = ColorCodeUtil.normalizeBaseColorValue(baseColorValue);
        String text = visiblePlainText == null ? "" : visiblePlainText;
        List<TextSpan> spans = text.isEmpty()
            ? List.of()
            : List.of(new TextSpan(
                text,
                normalizedBaseColor == null ? "&f" : normalizedBaseColor,
                "",
                null,
                null,
                gradientColors,
                0,
                text.length()
            ));
        return new PreparedPublicChatMessage(
            text,
            text,
            normalizedBaseColor == null ? "&f" : normalizedBaseColor,
            false,
            false,
            spans
        );
    }

    private void appendLiteral(StringBuilder currentText, StringBuilder visible, StringBuilder formatted, String literal) {
        currentText.append(literal);
        visible.append(literal);
        formatted.append(literal);
    }

    private void flush(List<TextSpan> spans, StringBuilder currentText, FormattingState state, int visibleLength) {
        if (currentText.isEmpty()) {
            return;
        }
        int gradientStart = Math.max(0, visibleLength - currentText.length());
        spans.add(new TextSpan(
            currentText.toString(),
            state.currentColorValue(),
            state.formatCodeSuffix(),
            null,
            null,
            state.currentGradientColors(),
            gradientStart,
            0
        ));
        currentText.setLength(0);
    }

    private String matchAmpersandRgb(String input, int index) {
        if (index + 8 > input.length() || input.charAt(index) != '&' || input.charAt(index + 1) != '#') {
            return null;
        }
        String candidate = input.substring(index + 2, index + 8);
        return candidate.matches("(?i)[0-9a-f]{6}") ? candidate.toUpperCase() : null;
    }

    private String matchMiniRgb(String input, int index) {
        if (index + 9 > input.length() || input.charAt(index) != '<' || input.charAt(index + 1) != '#') {
            return null;
        }
        if (input.charAt(index + 8) != '>') {
            return null;
        }
        String candidate = input.substring(index + 2, index + 8);
        return candidate.matches("(?i)[0-9a-f]{6}") ? candidate.toUpperCase() : null;
    }

    private String matchCloseMiniRgb(String input, int index) {
        if (index + 10 > input.length() || !matchesIgnoreCase(input, index, "</#")) {
            return null;
        }
        if (input.charAt(index + 9) != '>') {
            return null;
        }
        String candidate = input.substring(index + 3, index + 9);
        return candidate.matches("(?i)[0-9a-f]{6}") ? candidate.toUpperCase() : null;
    }

    private String matchColorTagRgb(String input, int index) {
        if (index + 15 > input.length() || !matchesIgnoreCase(input, index, "<color:#")) {
            return null;
        }
        if (input.charAt(index + 14) != '>') {
            return null;
        }
        String candidate = input.substring(index + 8, index + 14);
        return candidate.matches("(?i)[0-9a-f]{6}") ? candidate.toUpperCase() : null;
    }

    private boolean matchesIgnoreCase(String input, int index, String token) {
        return input.regionMatches(true, index, token, 0, token.length());
    }

    private static final class FormattingState {

        private final String baseColorValue;
        private final List<String> baseGradientColors;
        private String currentColorValue;
        private List<String> currentGradientColors;
        private boolean obfuscated;
        private boolean bold;
        private boolean strikethrough;
        private boolean underlined;
        private boolean italic;

        private FormattingState(String baseColorValue, List<String> baseGradientColors) {
            this.baseColorValue = baseColorValue;
            this.baseGradientColors = ColorGradientUtil.normalizeGradientColors(baseGradientColors);
            this.currentColorValue = baseColorValue;
            this.currentGradientColors = this.baseGradientColors;
        }

        private void applyColor(String colorValue) {
            currentColorValue = colorValue;
            currentGradientColors = List.of();
            obfuscated = false;
            bold = false;
            strikethrough = false;
            underlined = false;
            italic = false;
        }

        private void reset() {
            currentColorValue = baseColorValue;
            currentGradientColors = baseGradientColors;
            obfuscated = false;
            bold = false;
            strikethrough = false;
            underlined = false;
            italic = false;
        }

        private void applyFormat(char code) {
            switch (code) {
                case 'k' -> obfuscated = true;
                case 'l' -> bold = true;
                case 'm' -> strikethrough = true;
                case 'n' -> underlined = true;
                case 'o' -> italic = true;
                default -> {
                }
            }
        }

        private String currentColorValue() {
            return currentColorValue;
        }

        private List<String> currentGradientColors() {
            return currentGradientColors;
        }

        private String formatCodeSuffix() {
            StringBuilder builder = new StringBuilder();
            if (obfuscated) {
                builder.append("&k");
            }
            if (bold) {
                builder.append("&l");
            }
            if (strikethrough) {
                builder.append("&m");
            }
            if (underlined) {
                builder.append("&n");
            }
            if (italic) {
                builder.append("&o");
            }
            return builder.toString();
        }
    }

    public record PermissionAccess(boolean legacyAllowed, boolean formatAllowed, boolean rgbAllowed) {
    }

    public record TextSpan(
        String text,
        String colorValue,
        String formatCodes,
        Component hover,
        ClickEvent click,
        List<String> gradientColors,
        int gradientStart,
        int gradientLength
    ) {

        public TextSpan {
            text = text == null ? "" : text;
            formatCodes = formatCodes == null ? "" : formatCodes;
            gradientColors = ColorGradientUtil.normalizeGradientColors(gradientColors);
            gradientStart = Math.max(0, gradientStart);
            gradientLength = gradientLength <= 0 ? text.length() : gradientLength;
        }

        public TextSpan(String text, String colorValue, String formatCodes, Component hover, ClickEvent click) {
            this(text, colorValue, formatCodes, hover, click, List.of(), 0, 0);
        }

        public String stylePrefix() {
            return (colorValue == null ? "" : colorValue) + (formatCodes == null ? "" : formatCodes);
        }

        public boolean hasGradient() {
            return gradientColors.size() >= 2;
        }

        public TextSpan slice(int start, int end) {
            int safeStart = Math.max(0, Math.min(start, text.length()));
            int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
            return new TextSpan(
                text.substring(safeStart, safeEnd),
                colorValue,
                formatCodes,
                hover,
                click,
                gradientColors,
                gradientStart + safeStart,
                gradientLength
            );
        }

        public TextSpan withGradientLength(int length) {
            return new TextSpan(text, colorValue, formatCodes, hover, click, gradientColors, gradientStart, length);
        }

        public TextSpan withTextAndStyle(
            String newText,
            String newColorValue,
            String newFormatCodes,
            Component newHover,
            ClickEvent newClick,
            List<String> newGradientColors,
            int newGradientStart
        ) {
            return new TextSpan(
                newText,
                newColorValue,
                newFormatCodes,
                newHover,
                newClick,
                newGradientColors,
                newGradientStart,
                gradientLength
            );
        }

        public Component toComponent() {
            if (hasGradient()) {
                return ColorGradientUtil.component(text, gradientColors, gradientStart, gradientLength, formatCodes, hover, click);
            }
            Component component = Component.text(text).style(RichText.styleOf(stylePrefix()));
            if (hover != null) {
                component = component.hoverEvent(HoverEvent.showText(hover));
            }
            if (click != null) {
                component = component.clickEvent(click);
            }
            return component;
        }
    }

    public record PreparedPublicChatMessage(
        String visiblePlainText,
        String formattedRawMessage,
        String baseColorValue,
        boolean usedInlineFormatting,
        boolean hadUnauthorizedFormatting,
        List<TextSpan> spans
    ) {

        public Component toComponent() {
            TextComponent.Builder builder = Component.text();
            for (TextSpan span : spans) {
                if (span.text() == null || span.text().isEmpty()) {
                    continue;
                }
                builder.append(span.toComponent());
            }
            return builder.build();
        }
    }
}
