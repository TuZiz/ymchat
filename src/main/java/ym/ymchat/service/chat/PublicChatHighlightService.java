package ym.ymchat.service.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import ym.ymchat.config.highlight.KeywordHighlightRule;
import ym.ymchat.config.highlight.PatternHighlightRule;
import ym.ymchat.config.highlight.PublicChatHighlightSettings;

import ym.ymchat.service.color.ColorCodeUtil;
import ym.ymchat.service.color.PublicChatColorService;
public final class PublicChatHighlightService {

    public PublicChatColorService.PreparedPublicChatMessage apply(
        PublicChatColorService.PreparedPublicChatMessage message,
        String visiblePlainText,
        String channelId,
        PublicChatHighlightSettings settings
    ) {
        if (message == null || settings == null || !settings.enabled()) {
            return message;
        }

        String text = visiblePlainText == null ? "" : visiblePlainText;
        if (text.isEmpty() || message.spans().isEmpty()) {
            return message;
        }

        HighlightStyle[] coverage = new HighlightStyle[text.length()];
        int order = 0;

        for (KeywordHighlightRule rule : settings.keywordRules()) {
            if (!rule.enabled() || !settings.appliesToChannel(rule.channels(), channelId)) {
                continue;
            }
            Pattern pattern = compileKeywordPattern(rule);
            if (pattern == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (matcher.end() <= matcher.start()) {
                    continue;
                }
                if (rule.wholeWord() && !wholeWord(text, matcher.start(), matcher.end())) {
                    continue;
                }
                HighlightStyle style = HighlightStyle.from(rule, order++);
                if (style.visibleEffect()) {
                    applyMatch(coverage, matcher.start(), matcher.end(), style);
                }
            }
        }

        for (PatternHighlightRule rule : settings.patternRules()) {
            if (!rule.enabled() || !settings.appliesToChannel(rule.channels(), channelId)) {
                continue;
            }
            HighlightStyle style = HighlightStyle.from(rule, order++);
            if (!style.visibleEffect()) {
                continue;
            }
            for (String expression : rule.regexes()) {
                Pattern pattern = compilePattern(expression, 0);
                if (pattern == null) {
                    continue;
                }
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    if (matcher.end() <= matcher.start()) {
                        continue;
                    }
                    applyMatch(coverage, matcher.start(), matcher.end(), style);
                }
            }
        }

        if (!hasCoverage(coverage)) {
            return message;
        }

        List<PublicChatColorService.TextSpan> highlightedSpans = rebuildSpans(message.spans(), coverage);
        return new PublicChatColorService.PreparedPublicChatMessage(
            message.visiblePlainText(),
            message.formattedRawMessage(),
            message.baseColorValue(),
            message.usedInlineFormatting(),
            message.hadUnauthorizedFormatting(),
            highlightedSpans
        );
    }

    private Pattern compileKeywordPattern(KeywordHighlightRule rule) {
        if (rule.match() == null || rule.match().isBlank()) {
            return null;
        }
        int flags = rule.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return compilePattern(rule.regex() ? rule.match() : Pattern.quote(rule.match()), flags);
    }

    private Pattern compilePattern(String expression, int flags) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(expression, flags);
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }

    private void applyMatch(HighlightStyle[] coverage, int start, int end, HighlightStyle candidate) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(coverage.length, end);
        for (int index = safeStart; index < safeEnd; index++) {
            HighlightStyle existing = coverage[index];
            if (existing == null || candidate.betterThan(existing)) {
                coverage[index] = candidate;
            }
        }
    }

    private boolean hasCoverage(HighlightStyle[] coverage) {
        for (HighlightStyle style : coverage) {
            if (style != null) {
                return true;
            }
        }
        return false;
    }

    private List<PublicChatColorService.TextSpan> rebuildSpans(
        List<PublicChatColorService.TextSpan> spans,
        HighlightStyle[] coverage
    ) {
        List<PublicChatColorService.TextSpan> rebuilt = new ArrayList<>();
        int globalIndex = 0;

        for (PublicChatColorService.TextSpan span : spans) {
            String spanText = span.text();
            if (spanText == null || spanText.isEmpty()) {
                continue;
            }

            int localStart = 0;
            HighlightStyle active = globalIndex < coverage.length ? coverage[globalIndex] : null;
            for (int offset = 0; offset < spanText.length(); offset++) {
                HighlightStyle current = globalIndex + offset < coverage.length ? coverage[globalIndex + offset] : null;
                if (!sameStyle(active, current)) {
                    rebuilt.add(buildSpan(span, spanText.substring(localStart, offset), active));
                    localStart = offset;
                    active = current;
                }
            }
            rebuilt.add(buildSpan(span, spanText.substring(localStart), active));
            globalIndex += spanText.length();
        }

        return rebuilt.stream()
            .filter(candidate -> candidate.text() != null && !candidate.text().isEmpty())
            .toList();
    }

    private PublicChatColorService.TextSpan buildSpan(
        PublicChatColorService.TextSpan base,
        String text,
        HighlightStyle highlight
    ) {
        if (highlight == null) {
            return new PublicChatColorService.TextSpan(text, base.colorValue(), base.formatCodes());
        }
        String color = highlight.colorValue() == null ? base.colorValue() : highlight.colorValue();
        return new PublicChatColorService.TextSpan(text, color, mergeFormatCodes(base.formatCodes(), highlight.formatCodes()));
    }

    private boolean sameStyle(HighlightStyle left, HighlightStyle right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.priority() == right.priority()
            && left.order() == right.order()
            && equalsIgnoreCase(left.colorValue(), right.colorValue())
            && left.formatCodes().equals(right.formatCodes());
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return right != null && left.equalsIgnoreCase(right);
    }

    private boolean wholeWord(String text, int start, int end) {
        return boundary(text, start - 1) && boundary(text, end);
    }

    private boolean boundary(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return true;
        }
        char character = text.charAt(index);
        return !Character.isLetterOrDigit(character) && character != '_';
    }

    private String mergeFormatCodes(String baseFormats, String extraFormats) {
        FormatState state = new FormatState();
        state.apply(baseFormats);
        state.apply(extraFormats);
        return state.toFormatCodes();
    }

    private record HighlightStyle(String colorValue, String formatCodes, int priority, int order) {

        private static HighlightStyle from(KeywordHighlightRule rule, int order) {
            return new HighlightStyle(
                ColorCodeUtil.normalizeBaseColorValue(rule.color()),
                toFormatCodes(rule.formats()),
                rule.priority(),
                order
            );
        }

        private static HighlightStyle from(PatternHighlightRule rule, int order) {
            return new HighlightStyle(
                ColorCodeUtil.normalizeBaseColorValue(rule.color()),
                toFormatCodes(rule.formats()),
                rule.priority(),
                order
            );
        }

        private boolean betterThan(HighlightStyle other) {
            if (priority != other.priority()) {
                return priority > other.priority();
            }
            return order < other.order();
        }

        private boolean visibleEffect() {
            return colorValue != null || (formatCodes != null && !formatCodes.isBlank());
        }
    }

    private static String toFormatCodes(List<String> formats) {
        if (formats == null || formats.isEmpty()) {
            return "";
        }
        FormatState state = new FormatState();
        for (String format : formats) {
            if (format == null || format.isBlank()) {
                continue;
            }
            switch (format.trim().toLowerCase(Locale.ROOT)) {
                case "k", "obfuscated", "magic" -> state.obfuscated = true;
                case "l", "bold" -> state.bold = true;
                case "m", "strikethrough", "strike" -> state.strikethrough = true;
                case "n", "underlined", "underline" -> state.underlined = true;
                case "o", "italic" -> state.italic = true;
                default -> {
                }
            }
        }
        return state.toFormatCodes();
    }

    private static final class FormatState {

        private boolean obfuscated;
        private boolean bold;
        private boolean strikethrough;
        private boolean underlined;
        private boolean italic;

        private void apply(String formatCodes) {
            if (formatCodes == null || formatCodes.isBlank()) {
                return;
            }
            String normalized = formatCodes.toLowerCase(Locale.ROOT);
            for (int index = 0; index < normalized.length() - 1; index++) {
                if (normalized.charAt(index) != '&') {
                    continue;
                }
                switch (normalized.charAt(index + 1)) {
                    case 'k' -> obfuscated = true;
                    case 'l' -> bold = true;
                    case 'm' -> strikethrough = true;
                    case 'n' -> underlined = true;
                    case 'o' -> italic = true;
                    default -> {
                    }
                }
                index++;
            }
        }

        private String toFormatCodes() {
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
}
