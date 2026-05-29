package ym.ymchat.service.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import ym.ymchat.config.highlight.KeywordHighlightRule;
import ym.ymchat.config.highlight.PatternHighlightRule;
import ym.ymchat.config.highlight.PublicChatHighlightSettings;

import ym.ymchat.service.color.ColorCodeUtil;
import ym.ymchat.service.color.PublicChatColorService;
import ym.ymchat.service.text.RichText;
public final class PublicChatHighlightService {

    private static final Set<String> SYSTEM_HOVER_LABELS = Set.of(
        "交易关键词",
        "玩家求购",
        "出售信息",
        "出售货架",
        "求助信息",
        "紧急求助",
        "组队信息",
        "队伍招募",
        "交易报价",
        "物品数量",
        "目标坐标",
        "时间节点",
        "价格",
        "数量",
        "坐标",
        "时间"
    );

    public PublicChatColorService.PreparedPublicChatMessage apply(
        PublicChatColorService.PreparedPublicChatMessage message,
        String visiblePlainText,
        String channelId,
        PublicChatHighlightSettings settings
    ) {
        return apply(message, visiblePlainText, channelId, null, settings);
    }

    public PublicChatColorService.PreparedPublicChatMessage apply(
        PublicChatColorService.PreparedPublicChatMessage message,
        String visiblePlainText,
        String channelId,
        Player sender,
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
            for (String expression : rule.effectiveMatches()) {
                Pattern pattern = compileKeywordPattern(rule, expression);
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
                    HighlightStyle style = HighlightStyle.from(rule, order++, text, senderName(sender));
                    if (style.visibleEffect()) {
                        applyMatch(coverage, matcher.start(), matcher.end(), style);
                    }
                }
            }
        }

        for (PatternHighlightRule rule : settings.patternRules()) {
            if (!rule.enabled() || !settings.appliesToChannel(rule.channels(), channelId)) {
                continue;
            }
            HighlightStyle style = HighlightStyle.from(rule, order++, text, senderName(sender));
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

    private Pattern compileKeywordPattern(KeywordHighlightRule rule, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        int flags = rule.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return compilePattern(rule.regex() ? expression : Pattern.quote(expression), flags);
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
                    rebuilt.add(buildSpan(span, spanText.substring(localStart, offset), active, localStart));
                    localStart = offset;
                    active = current;
                }
            }
            rebuilt.add(buildSpan(span, spanText.substring(localStart), active, localStart));
            globalIndex += spanText.length();
        }

        return rebuilt.stream()
            .filter(candidate -> candidate.text() != null && !candidate.text().isEmpty())
            .toList();
    }

    private PublicChatColorService.TextSpan buildSpan(
        PublicChatColorService.TextSpan base,
        String text,
        HighlightStyle highlight,
        int localStart
    ) {
        PublicChatColorService.TextSpan segment = base.slice(localStart, localStart + text.length());
        if (highlight == null) {
            return segment;
        }
        String color = highlight.colorValue() == null ? segment.colorValue() : highlight.colorValue();
        List<String> gradientColors = highlight.colorValue() == null ? segment.gradientColors() : List.of();
        return segment.withTextAndStyle(
            text,
            color,
            mergeFormatCodes(segment.formatCodes(), highlight.formatCodes()),
            highlight.hover() == null ? segment.hover() : highlight.hover(),
            highlight.click() == null ? segment.click() : highlight.click(),
            gradientColors,
            segment.gradientStart()
        );
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
            && left.formatCodes().equals(right.formatCodes())
            && sameComponent(left.hover(), right.hover())
            && sameClick(left.click(), right.click());
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return right != null && left.equalsIgnoreCase(right);
    }

    private boolean sameComponent(Component left, Component right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean sameClick(ClickEvent left, ClickEvent right) {
        return left == null ? right == null : left.equals(right);
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

    private record HighlightStyle(String colorValue, String formatCodes, int priority, int order, Component hover, ClickEvent click) {

        private static HighlightStyle from(KeywordHighlightRule rule, int order, String message, String playerName) {
            return new HighlightStyle(
                ColorCodeUtil.normalizeBaseColorValue(rule.color()),
                toFormatCodes(rule.formats()),
                rule.priority(),
                order,
                buildHover(rule.hover(), message, playerName),
                buildClick(rule.suggest(), rule.command(), rule.copy(), message, playerName)
            );
        }

        private static HighlightStyle from(PatternHighlightRule rule, int order, String message, String playerName) {
            return new HighlightStyle(
                ColorCodeUtil.normalizeBaseColorValue(rule.color()),
                toFormatCodes(rule.formats()),
                rule.priority(),
                order,
                buildHover(rule.hover(), message, playerName),
                buildClick(rule.suggest(), rule.command(), rule.copy(), message, playerName)
            );
        }

        private boolean betterThan(HighlightStyle other) {
            if (priority != other.priority()) {
                return priority > other.priority();
            }
            return order < other.order();
        }

        private boolean visibleEffect() {
            return colorValue != null
                || (formatCodes != null && !formatCodes.isBlank())
                || hover != null
                || click != null;
        }
    }

    private static Component buildHover(List<String> hoverLines, String message, String playerName) {
        if (hoverLines == null || hoverLines.isEmpty()) {
            return null;
        }
        Component result = Component.empty();
        boolean first = true;
        for (String line : hoverLines) {
            if (line == null) {
                continue;
            }
            String resolved = placeholders(line, message, playerName);
            if (systemHoverLabel(resolved)) {
                continue;
            }
            if (!first) {
                result = result.append(Component.newline());
            }
            result = result.append(RichText.deserialize(resolved));
            first = false;
        }
        return first ? null : result;
    }

    private static ClickEvent buildClick(String suggest, String command, String copy, String message, String playerName) {
        if (suggest != null && !suggest.isBlank()) {
            return ClickEvent.suggestCommand(placeholders(suggest, message, playerName));
        }
        if (command != null && !command.isBlank()) {
            return ClickEvent.runCommand(placeholders(command, message, playerName));
        }
        if (copy != null && !copy.isBlank()) {
            return ClickEvent.copyToClipboard(placeholders(copy, message, playerName));
        }
        return null;
    }

    private static String placeholders(String value, String message, String playerName) {
        return value
            .replace("%message%", message == null ? "" : message)
            .replace("%player_name%", playerName == null ? "" : playerName);
    }

    private static boolean systemHoverLabel(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String plain = value
            .replaceAll("&#[0-9A-Fa-f]{6}", "")
            .replaceAll("(?i)[&§][0-9a-fk-or]", "")
            .replace("✦", "")
            .trim()
            .replaceAll("\\s+", " ");
        return SYSTEM_HOVER_LABELS.contains(plain);
    }

    private static String senderName(Player sender) {
        return sender == null ? "" : sender.getName();
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
