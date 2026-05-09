package ym.ymchat.service.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

public final class ConditionEvaluator {

    private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern COMPARISON = Pattern.compile("^(placeholder|papi)\\s+\"([^\"]+)\"\\s+(equals|contains|startswith)\\s+\"([^\"]*)\"$", Pattern.CASE_INSENSITIVE);

    private ConditionEvaluator() {
    }

    public static boolean evaluate(Player player, String rawCondition) {
        if (rawCondition == null || rawCondition.isBlank() || "~".equals(rawCondition.trim())) {
            return true;
        }

        String condition = stripOuterParentheses(rawCondition.trim());
        List<String> orParts = splitTopLevel(condition, "||");
        if (orParts.size() > 1) {
            for (String orPart : orParts) {
                if (evaluate(player, orPart)) {
                    return true;
                }
            }
            return false;
        }

        List<String> andParts = splitTopLevel(condition, "&&");
        if (andParts.size() > 1) {
            for (String andPart : andParts) {
                if (!evaluate(player, andPart)) {
                    return false;
                }
            }
            return true;
        }

        if (condition.startsWith("!")) {
            return !evaluate(player, condition.substring(1).trim());
        }

        String normalized = condition.toLowerCase(Locale.ROOT);
        if ("player op".equals(normalized)) {
            return player.isOp();
        }
        if (normalized.startsWith("perm ") || normalized.startsWith("permission ")) {
            String permission = extractQuotedValue(condition);
            return permission != null && player.hasPermission(permission);
        }
        if (normalized.startsWith("!perm ") || normalized.startsWith("!permission ")) {
            String permission = extractQuotedValue(condition.substring(1));
            return permission == null || !player.hasPermission(permission);
        }
        if (normalized.startsWith("world ")) {
            String world = extractQuotedValue(condition);
            return world != null && player.getWorld().getName().equalsIgnoreCase(world);
        }
        Matcher comparison = COMPARISON.matcher(condition);
        if (comparison.matches()) {
            String placeholder = comparison.group(2);
            String operator = comparison.group(3).toLowerCase(Locale.ROOT);
            String expected = comparison.group(4);
            String actual = PlaceholderResolver.resolve(player, placeholder);
            return switch (operator) {
                case "equals" -> actual.equals(expected);
                case "contains" -> actual.contains(expected);
                case "startswith" -> actual.startsWith(expected);
                default -> false;
            };
        }
        return true;
    }

    private static List<String> splitTopLevel(String input, String token) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inQuote = false;
        int start = 0;

        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote) {
                if (current == '(') {
                    depth++;
                } else if (current == ')') {
                    depth = Math.max(0, depth - 1);
                }
            }
            if (!inQuote && depth == 0 && input.startsWith(token, index)) {
                parts.add(input.substring(start, index).trim());
                start = index + token.length();
                index += token.length() - 1;
            }
        }

        parts.add(input.substring(start).trim());
        return parts;
    }

    private static String stripOuterParentheses(String input) {
        String result = input;
        while (result.startsWith("(") && result.endsWith(")")) {
            String inner = result.substring(1, result.length() - 1).trim();
            if (!isBalanced(inner)) {
                break;
            }
            result = inner;
        }
        return result;
    }

    private static boolean isBalanced(String input) {
        int depth = 0;
        boolean inQuote = false;
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (inQuote) {
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0 && !inQuote;
    }

    private static String extractQuotedValue(String input) {
        Matcher matcher = QUOTED_VALUE.matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }
}
