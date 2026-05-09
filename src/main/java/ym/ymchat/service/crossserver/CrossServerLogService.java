package ym.ymchat.service.crossserver;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.command.CommandMessages;
import ym.ymchat.config.crossserver.CrossServerLogSettings;

import ym.ymchat.service.language.LanguageService;
import ym.ymchat.service.text.RichText;
public final class CrossServerLogService {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Map<String, String> SUPPORTED_KEYS = Map.ofEntries(
        Map.entry("player", "player"),
        Map.entry("channel", "channel"),
        Map.entry("keyword", "keyword"),
        Map.entry("since", "since"),
        Map.entry("time", "since"),
        Map.entry("page", "page"),
        Map.entry("limit", "limit")
    );
    private static final DateTimeFormatter EXACT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final YmChatPlugin plugin;

    public CrossServerLogService(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handleLogs(CommandSender sender, String[] args) {
        CrossServerLogSettings settings = plugin.getChatConfig().crossServerSettings().logs();
        if (!settings.enabled()) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.logs.disabled");
            return true;
        }
        if (!sender.hasPermission(settings.permission())) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.logs.no-permission");
            return true;
        }
        if (!plugin.getCrossServerChatService().isEnabled()) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.logs.unavailable");
            return true;
        }

        ParseResult parseResult = parseArguments(List.of(args), settings);
        if (parseResult.helpRequested()) {
            sendHelp(sender);
            return true;
        }
        if (!parseResult.valid()) {
            CommandMessages.sendKey(
                plugin,
                sender,
                "commands.ymchat.logs.invalid-argument",
                "argument", formatInvalidArgument(parseResult.errorMessage())
            );
            sendHelp(sender);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runQuery(sender, parseResult.query(), settings));
        return true;
    }

    void runQuery(CommandSender sender, ParsedQuery parsedQuery, CrossServerLogSettings settings) {
        try {
            CrossServerChatService.LogQueryResult result = plugin.getCrossServerChatService().queryLogs(parsedQuery.toLogQuery());
            if (sender instanceof Player player) {
                plugin.getPlatformBridge().runForPlayer(player, () -> {
                    if (player.isOnline()) {
                        sendPlayerResults(player, parsedQuery, result, settings);
                    }
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> sendConsoleResults(sender, parsedQuery, result, settings));
        } catch (IllegalStateException exception) {
            Bukkit.getScheduler().runTask(plugin, () -> CommandMessages.sendKey(plugin, sender, "commands.ymchat.logs.unavailable"));
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to query cross-server logs: " + exception.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> CommandMessages.sendKey(plugin, sender, "commands.ymchat.logs.query-failed"));
        }
    }

    private void sendPlayerResults(Player player, ParsedQuery parsedQuery, CrossServerChatService.LogQueryResult result, CrossServerLogSettings settings) {
        player.sendMessage(buildHeader(parsedQuery, result, settings));
        if (result.entries().isEmpty()) {
            CommandMessages.sendKey(plugin, player, "commands.ymchat.logs.no-results");
            return;
        }
        for (CrossServerChatService.LogEntry entry : result.entries()) {
            plugin.getPlatformBridge().sendMessage(player, buildLine(entry, settings));
        }
        plugin.getPlatformBridge().sendMessage(player, buildFooter(parsedQuery, result, settings));
    }

    private void sendConsoleResults(CommandSender sender, ParsedQuery parsedQuery, CrossServerChatService.LogQueryResult result, CrossServerLogSettings settings) {
        CommandMessages.send(sender, PLAIN.serialize(buildHeader(parsedQuery, result, settings)));
        if (result.entries().isEmpty()) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.logs.no-results");
            return;
        }
        for (CrossServerChatService.LogEntry entry : result.entries()) {
            CommandMessages.send(sender, PLAIN.serialize(buildLine(entry, settings)));
        }
        CommandMessages.send(sender, buildConsoleFooter(parsedQuery, result, settings));
    }

    Component buildHeader(ParsedQuery parsedQuery, CrossServerChatService.LogQueryResult result, CrossServerLogSettings settings) {
        return RichText.deserialize(replacePlaceholders(settings.headerFormat(), Map.of(
            "filters", parsedQuery.describeFilters(plugin.getLanguageService()),
            "count", String.valueOf(result.totalCount())
        )));
    }

    Component buildLine(CrossServerChatService.LogEntry entry, CrossServerLogSettings settings) {
        DateTimeFormatter formatter = createFormatter(settings.timestampFormat());
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", String.valueOf(entry.id()));
        placeholders.put("time", formatter.format(entry.createdAt().atZone(ZoneId.systemDefault())));
        placeholders.put("time_exact", EXACT_TIME.format(entry.createdAt().atZone(ZoneId.systemDefault())));
        placeholders.put("server", entry.serverName());
        placeholders.put("server_id", entry.serverId());
        placeholders.put("channel", entry.channelId());
        placeholders.put("sender", entry.senderName());

        Component hover = RichText.deserialize(String.join("\n", replacePlaceholders(settings.hoverFormat(), placeholders)));
        String copiedText = buildCopiedText(entry, formatter);
        Component line = buildTemplateWithMessage(settings.lineFormat(), placeholders, entry.plainText());
        return line.hoverEvent(HoverEvent.showText(hover))
            .clickEvent(ClickEvent.copyToClipboard(copiedText));
    }

    Component buildFooter(ParsedQuery parsedQuery, CrossServerChatService.LogQueryResult result, CrossServerLogSettings settings) {
        int currentPage = Math.max(1, result.page());
        int totalPages = Math.max(1, result.totalPages());
        String previousCommand = parsedQuery.toCommand(Math.max(1, currentPage - 1));
        String nextCommand = parsedQuery.toCommand(Math.min(totalPages, currentPage + 1));
        Component previous = pageButton("commands.ymchat.logs.pagination.previous", currentPage > 1, previousCommand);
        Component next = pageButton("commands.ymchat.logs.pagination.next", currentPage < totalPages, nextCommand);
        return buildFooterTemplate(settings.footerFormat(), currentPage, totalPages, previous, next);
    }

    String buildConsoleFooter(ParsedQuery parsedQuery, CrossServerChatService.LogQueryResult result, CrossServerLogSettings settings) {
        int currentPage = Math.max(1, result.page());
        int totalPages = Math.max(1, result.totalPages());
        String template = settings.footerFormat()
            .replace("%previous%", currentPage > 1 ? parsedQuery.toCommand(Math.max(1, currentPage - 1)) : "-")
            .replace("%next%", currentPage < totalPages ? parsedQuery.toCommand(Math.min(totalPages, currentPage + 1)) : "-");
        return replacePlaceholders(template, Map.of(
            "page", String.valueOf(currentPage),
            "pages", String.valueOf(totalPages)
        ));
    }

    private Component buildFooterTemplate(String template, int page, int pages, Component previous, Component next) {
        String resolved = template
            .replace("%page%", String.valueOf(page))
            .replace("%pages%", String.valueOf(pages));
        if (resolved.contains("%previous%") || resolved.contains("%next%")) {
            TextComponent.Builder builder = Component.text();
            int cursor = 0;
            while (cursor < resolved.length()) {
                int previousIndex = resolved.indexOf("%previous%", cursor);
                int nextIndex = resolved.indexOf("%next%", cursor);
                int nextTokenIndex;
                String token;
                if (previousIndex >= 0 && (nextIndex < 0 || previousIndex < nextIndex)) {
                    nextTokenIndex = previousIndex;
                    token = "%previous%";
                } else if (nextIndex >= 0) {
                    nextTokenIndex = nextIndex;
                    token = "%next%";
                } else {
                    builder.append(RichText.deserialize(resolved.substring(cursor)));
                    break;
                }
                if (nextTokenIndex > cursor) {
                    builder.append(RichText.deserialize(resolved.substring(cursor, nextTokenIndex)));
                }
                builder.append("%previous%".equals(token) ? previous : next);
                cursor = nextTokenIndex + token.length();
            }
            return builder.build();
        }
        return previous.append(Component.space()).append(RichText.deserialize(resolved)).append(Component.space()).append(next);
    }

    private Component buildTemplateWithMessage(String template, Map<String, String> placeholders, String message) {
        String resolved = replacePlaceholders(template, placeholders);
        if (!resolved.contains("%message%")) {
            return RichText.deserialize(resolved);
        }
        TextComponent.Builder builder = Component.text();
        int cursor = 0;
        while (cursor < resolved.length()) {
            int tokenIndex = resolved.indexOf("%message%", cursor);
            if (tokenIndex < 0) {
                builder.append(RichText.deserialize(resolved.substring(cursor)));
                break;
            }
            if (tokenIndex > cursor) {
                builder.append(RichText.deserialize(resolved.substring(cursor, tokenIndex)));
            }
            builder.append(Component.text(message == null ? "" : message).style(RichText.styleOf("&f")));
            cursor = tokenIndex + "%message%".length();
        }
        return builder.build();
    }

    private Component pageButton(String key, boolean enabled, String command) {
        String text = plugin.getLanguageService().get(
            enabled ? key : key + "-disabled"
        );
        Component component = RichText.deserialize(text);
        if (!enabled) {
            return component;
        }
        return component.clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(HoverEvent.showText(RichText.deserialize(plugin.getLanguageService().get("commands.ymchat.logs.pagination.hover"))));
    }

    private String buildCopiedText(CrossServerChatService.LogEntry entry, DateTimeFormatter formatter) {
        return formatter.format(entry.createdAt().atZone(ZoneId.systemDefault()))
            + " [" + entry.serverName() + "/" + entry.channelId() + "] "
            + entry.senderName() + ": " + entry.plainText();
    }

    private DateTimeFormatter createFormatter(String pattern) {
        try {
            return DateTimeFormatter.ofPattern(pattern == null || pattern.isBlank() ? "MM-dd HH:mm:ss" : pattern);
        } catch (IllegalArgumentException exception) {
            return DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
        }
    }

    private String replacePlaceholders(String template, Map<String, String> placeholders) {
        String resolved = template == null ? "" : template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
        }
        return resolved;
    }

    private List<String> replacePlaceholders(List<String> templates, Map<String, String> placeholders) {
        List<String> resolved = new ArrayList<>(templates.size());
        for (String template : templates) {
            resolved.add(replacePlaceholders(template, placeholders));
        }
        return resolved;
    }


    private void sendHelp(CommandSender sender) {
        for (String line : plugin.getLanguageService().getList("commands.ymchat.logs.help")) {
            CommandMessages.send(sender, line);
        }
    }

    ParseResult parseArguments(List<String> args, CrossServerLogSettings settings) {
        if (args.isEmpty()) {
            return ParseResult.valid(ParsedQuery.defaults(settings));
        }
        if (args.size() == 1 && "help".equalsIgnoreCase(args.getFirst())) {
            return ParseResult.help();
        }

        Map<String, String> values = new LinkedHashMap<>();
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();

        for (String rawArg : args) {
            if (rawArg == null || rawArg.isBlank()) {
                continue;
            }
            int separator = rawArg.indexOf(':');
            if (separator > 0) {
                String key = rawArg.substring(0, separator).toLowerCase(Locale.ROOT);
                String value = rawArg.substring(separator + 1);
                String canonicalKey = SUPPORTED_KEYS.get(key);
                if (canonicalKey == null) {
                    return ParseResult.invalid(rawArg);
                }
                if (currentKey != null) {
                    values.put(currentKey, currentValue.toString().trim());
                }
                currentKey = canonicalKey;
                currentValue = new StringBuilder(value);
                continue;
            }
            if (currentKey == null) {
                return ParseResult.invalid(rawArg);
            }
            if (!currentValue.isEmpty()) {
                currentValue.append(' ');
            }
            currentValue.append(rawArg);
        }
        if (currentKey != null) {
            values.put(currentKey, currentValue.toString().trim());
        }

        try {
            String player = normalize(values.get("player"));
            String channel = resolveChannelFilter(normalize(values.get("channel")));
            String keyword = normalize(values.get("keyword"));
            String sinceInput = normalize(values.getOrDefault("since", settings.defaultSince()));
            Duration since = parseDuration(sinceInput);
            int limit = parsePositiveInt(values.getOrDefault("limit", String.valueOf(settings.defaultLimit())), settings.maxLimit(), "limit");
            int page = parsePositiveInt(values.getOrDefault("page", "1"), Integer.MAX_VALUE, "page");
            return ParseResult.valid(new ParsedQuery(player, channel, keyword, sinceInput, since, page, limit));
        } catch (IllegalArgumentException exception) {
            return ParseResult.invalid(exception.getMessage());
        }
    }

    static Duration parseDuration(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("since");
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            throw new IllegalArgumentException("since");
        }
        char unit = normalized.charAt(normalized.length() - 1);
        long value;
        try {
            value = Long.parseLong(normalized.substring(0, normalized.length() - 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("since");
        }
        if (value <= 0) {
            throw new IllegalArgumentException("since");
        }
        return switch (unit) {
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            case 'd' -> Duration.ofDays(value);
            default -> throw new IllegalArgumentException("since");
        };
    }

    static int parsePositiveInt(String input, int maxValue, String argumentName) {
        try {
            int value = Integer.parseInt(input);
            if (value <= 0) {
                throw new IllegalArgumentException(argumentName);
            }
            return Math.min(value, maxValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(argumentName);
        }
    }

    private String normalize(String input) {
        return input == null || input.isBlank() ? null : input.trim();
    }

    private String resolveChannelFilter(String input) {
        if (input == null || plugin == null || plugin.getChatConfig() == null) {
            return input;
        }
        ym.ymchat.config.chat.ChatChannel channel = plugin.getChatConfig().findChannel(input);
        return channel == null ? input : channel.id();
    }

    private String formatInvalidArgument(String argument) {
        if (plugin == null || plugin.getLanguageService() == null || argument == null) {
            return argument;
        }
        return switch (argument) {
            case "player" -> plugin.getLanguageService().get("commands.ymchat.logs.filters.player");
            case "channel" -> plugin.getLanguageService().get("commands.ymchat.logs.filters.channel");
            case "keyword" -> plugin.getLanguageService().get("commands.ymchat.logs.filters.keyword");
            case "since" -> plugin.getLanguageService().get("commands.ymchat.logs.filters.since");
            case "page" -> plugin.getLanguageService().get("commands.ymchat.logs.filters.page");
            case "limit" -> plugin.getLanguageService().get("commands.ymchat.logs.filters.limit");
            default -> argument;
        };
    }

    record ParseResult(boolean valid, boolean helpRequested, String errorMessage, ParsedQuery query) {

        static ParseResult valid(ParsedQuery query) {
            return new ParseResult(true, false, null, query);
        }

        static ParseResult invalid(String argument) {
            return new ParseResult(false, false, argument, null);
        }

        static ParseResult help() {
            return new ParseResult(false, true, null, null);
        }
    }

    record ParsedQuery(
        String player,
        String channel,
        String keyword,
        String sinceToken,
        Duration since,
        int page,
        int limit
    ) {

        static ParsedQuery defaults(CrossServerLogSettings settings) {
            String sinceToken = settings.defaultSince();
            Duration duration = parseDuration(sinceToken);
            return new ParsedQuery(null, null, null, sinceToken, duration, 1, settings.defaultLimit());
        }

        CrossServerChatService.LogQuery toLogQuery() {
            return new CrossServerChatService.LogQuery(
                player,
                channel,
                keyword,
                Instant.now().minus(since),
                page,
                limit
            );
        }

        String describeFilters(LanguageService languageService) {
            String noneValue = languageService == null ? "none" : languageService.get("common.none");
            StringJoiner joiner = new StringJoiner(", ");
            if (player != null) {
                joiner.add(label(languageService, "commands.ymchat.logs.filters.player", "player") + "=" + player);
            }
            if (channel != null) {
                joiner.add(label(languageService, "commands.ymchat.logs.filters.channel", "channel") + "=" + channel);
            }
            if (keyword != null) {
                joiner.add(label(languageService, "commands.ymchat.logs.filters.keyword", "keyword") + "=" + keyword);
            }
            joiner.add(label(languageService, "commands.ymchat.logs.filters.since", "since") + "=" + sinceToken);
            return joiner.length() == 0 ? noneValue : joiner.toString();
        }

        String toCommand(int targetPage) {
            List<String> parts = new ArrayList<>();
            if (player != null) {
                parts.add("player:" + player);
            }
            if (channel != null) {
                parts.add("channel:" + channel);
            }
            if (keyword != null) {
                parts.add("keyword:" + keyword);
            }
            parts.add("since:" + sinceToken);
            parts.add("page:" + targetPage);
            parts.add("limit:" + limit);
            return "/ymchat logs " + String.join(" ", parts);
        }

        private String label(LanguageService languageService, String key, String fallback) {
            return languageService == null ? fallback : languageService.get(key);
        }
    }
}
