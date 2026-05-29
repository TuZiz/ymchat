package ym.ymchat.service.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import ym.ymchat.config.filter.FilterRule;
import ym.ymchat.config.filter.FilterSettings;

public final class TextFilterService {

    private static final int MIN_FUZZY_CLOUD_WORD_CODE_POINTS = 3;
    private static final String ZERO_WIDTH_SEPARATORS = "\u200B\u200C\u200D\uFEFF";
    private static final String CLOUD_WORD_SEPARATOR_PATTERN = "[\\s\\p{Punct}\\p{P}" + ZERO_WIDTH_SEPARATORS + "]*";

    private final FilterCloudWordService cloudWordService;

    public TextFilterService(FilterCloudWordService cloudWordService) {
        this.cloudWordService = cloudWordService;
    }

    public FilterResult apply(Player player, String rawMessage, String scope, String channelId, FilterSettings settings) {
        if (!settings.enabled()) {
            return FilterResult.pass(rawMessage);
        }

        String result = rawMessage;
        List<String> hits = new ArrayList<>();
        boolean modified = false;

        for (FilterRule rule : settings.rules()) {
            if (player.hasPermission(rule.bypassPermission())) {
                continue;
            }
            if (!rule.appliesToScope(scope) || !rule.appliesToChannel(channelId)) {
                continue;
            }
            MatchOutcome outcome = applyRule(result, rule);
            if (!outcome.matched()) {
                continue;
            }
            hits.add(rule.id().isBlank() ? rule.match() : rule.id());
            if (rule.blockMode()) {
                return FilterResult.blocked(rule.message(), hits);
            }
            result = outcome.message();
            modified = true;
        }

        MatchOutcome cloudOutcome = applyCloudWords(player, result, scope, settings);
        if (cloudOutcome.matched()) {
            hits.add("cloud");
            if ("block".equalsIgnoreCase(settings.cloudSettings().mode())) {
                return FilterResult.blocked(settings.cloudSettings().message(), hits);
            }
            result = cloudOutcome.message();
            modified = true;
        }

        return FilterResult.pass(result, hits, modified);
    }

    private MatchOutcome applyCloudWords(Player player, String input, String scope, FilterSettings settings) {
        if (cloudWordService == null
            || settings.cloudSettings() == null
            || !settings.cloudSettings().enabled()
            || player.hasPermission(settings.cloudSettings().bypassPermission())
            || settings.cloudSettings().scopes().stream()
                .noneMatch(scopeValue -> "all".equalsIgnoreCase(scopeValue) || scopeValue.equalsIgnoreCase(scope))) {
            return MatchOutcome.noMatch(input);
        }

        String result = input;
        boolean matched = false;
        for (String word : cloudWordService.words(settings.cloudSettings())) {
            if (word == null || word.isBlank()) {
                continue;
            }
            MatchOutcome outcome = replaceCloudWord(result, word, settings.cloudSettings().replacement());
            if (outcome.matched()) {
                result = outcome.message();
                matched = true;
            }
        }
        return matched ? MatchOutcome.matched(result) : MatchOutcome.noMatch(input);
    }

    private MatchOutcome applyRule(String input, FilterRule rule) {
        if (rule.match() == null || rule.match().isBlank()) {
            return MatchOutcome.noMatch(input);
        }
        if (rule.regex()) {
            int flags = rule.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            Pattern pattern = Pattern.compile(rule.match(), flags);
            Matcher matcher = pattern.matcher(input);
            if (!matcher.find()) {
                return MatchOutcome.noMatch(input);
            }
            return MatchOutcome.matched(matcher.replaceAll(rule.replacement()));
        }

        String haystack = rule.caseSensitive() ? input : input.toLowerCase();
        String needle = rule.caseSensitive() ? rule.match() : rule.match().toLowerCase();
        if (!haystack.contains(needle)) {
            return MatchOutcome.noMatch(input);
        }
        return replaceLiteral(input, rule.match(), rule.replacement(), rule.caseSensitive());
    }

    private MatchOutcome replaceCloudWord(String input, String search, String replacement) {
        MatchOutcome literalOutcome = replaceLiteral(input, search, replacement, false);
        if (literalOutcome.matched()
            || searchableCodePointCount(search) < MIN_FUZZY_CLOUD_WORD_CODE_POINTS) {
            return literalOutcome;
        }
        return replaceSeparatedLiteral(input, search, replacement);
    }

    private MatchOutcome replaceSeparatedLiteral(String input, String search, String replacement) {
        String patternSource = separatedLiteralPattern(search);
        if (patternSource.isBlank()) {
            return MatchOutcome.noMatch(input);
        }
        Pattern pattern = Pattern.compile(
            patternSource,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS
        );
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return MatchOutcome.noMatch(input);
        }
        return MatchOutcome.matched(matcher.replaceAll(Matcher.quoteReplacement(replacement)));
    }

    private String separatedLiteralPattern(String search) {
        StringBuilder pattern = new StringBuilder();
        search.codePoints()
            .filter(codePoint -> !Character.isWhitespace(codePoint))
            .forEach(codePoint -> {
                if (pattern.length() > 0) {
                    pattern.append(CLOUD_WORD_SEPARATOR_PATTERN);
                }
                pattern.append(Pattern.quote(new String(Character.toChars(codePoint))));
            });
        return pattern.toString();
    }

    private int searchableCodePointCount(String search) {
        return (int) search.codePoints()
            .filter(codePoint -> !Character.isWhitespace(codePoint))
            .count();
    }

    private MatchOutcome replaceLiteral(String input, String search, String replacement, boolean caseSensitive) {
        if (caseSensitive) {
            if (!input.contains(search)) {
                return MatchOutcome.noMatch(input);
            }
            return MatchOutcome.matched(input.replace(search, replacement));
        }
        Pattern pattern = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return MatchOutcome.noMatch(input);
        }
        return MatchOutcome.matched(matcher.replaceAll(Matcher.quoteReplacement(replacement)));
    }

    private record MatchOutcome(boolean matched, String message) {

        private static MatchOutcome noMatch(String message) {
            return new MatchOutcome(false, message);
        }

        private static MatchOutcome matched(String message) {
            return new MatchOutcome(true, message);
        }
    }

    public record FilterResult(boolean allowed, String message, List<String> hits, boolean modified) {

        public static FilterResult pass(String message) {
            return new FilterResult(true, message, List.of(), false);
        }

        public static FilterResult pass(String message, List<String> hits, boolean modified) {
            return new FilterResult(true, message, List.copyOf(hits), modified);
        }

        public static FilterResult blocked(String message, List<String> hits) {
            return new FilterResult(false, message, List.copyOf(hits), false);
        }

        public String describeHits() {
            return hits.isEmpty() ? "none" : String.join(",", hits);
        }
    }
}
