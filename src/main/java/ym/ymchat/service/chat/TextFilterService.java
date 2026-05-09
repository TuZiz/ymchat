package ym.ymchat.service.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import ym.ymchat.config.filter.FilterRule;
import ym.ymchat.config.filter.FilterSettings;

public final class TextFilterService {

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

        return FilterResult.pass(result, hits, modified);
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
        return MatchOutcome.matched(replaceLiteral(input, rule.match(), rule.replacement(), rule.caseSensitive()));
    }

    private String replaceLiteral(String input, String search, String replacement, boolean caseSensitive) {
        if (caseSensitive) {
            return input.replace(search, replacement);
        }
        Pattern pattern = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return pattern.matcher(input).replaceAll(Matcher.quoteReplacement(replacement));
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
