package ym.ymchat.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;
import ym.ymchat.config.chat.AntiSpamSettings;
import ym.ymchat.config.chat.ChannelSwitchSettings;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.chat.FormatRule;
import ym.ymchat.config.chat.MentionSettings;
import ym.ymchat.config.chat.MessageOptions;
import ym.ymchat.config.chat.PrivateMessageRule;
import ym.ymchat.config.chat.PrivateMessageSettings;
import ym.ymchat.config.chat.SectionStyle;
import ym.ymchat.config.chat.TargetMode;
import ym.ymchat.config.color.ColorChatSettings;
import ym.ymchat.config.color.ColorPreset;
import ym.ymchat.config.color.FixedColorSettings;
import ym.ymchat.config.color.InlineColorSettings;
import ym.ymchat.config.crossserver.CrossServerLogSettings;
import ym.ymchat.config.crossserver.CrossServerSettings;
import ym.ymchat.config.crossserver.DatabaseSettings;
import ym.ymchat.config.filter.FilterCloudSettings;
import ym.ymchat.config.filter.FilterRule;
import ym.ymchat.config.filter.FilterSettings;
import ym.ymchat.config.highlight.KeywordHighlightRule;
import ym.ymchat.config.highlight.PatternHighlightRule;
import ym.ymchat.config.highlight.PublicChatHighlightSettings;
import ym.ymchat.config.megaphone.MegaphoneConfigParser;
import ym.ymchat.config.megaphone.MegaphoneSettings;
import ym.ymchat.config.showcase.ItemShowcaseConfigParser;
import ym.ymchat.config.showcase.ItemShowcaseSettings;
import ym.ymchat.service.language.LanguageService;

public final class ChatConfigLoader {

    private static final Pattern INLINE_CONDITION = Pattern.compile("\\{condition:\\s*(.+)}\\s*$");
    private static final String PRIVATE_MESSAGES_DISABLED = "lang:private-messages.messages.disabled";
    private static final String PRIVATE_MESSAGES_NO_REPLY_TARGET = "lang:private-messages.messages.no-reply-target";
    private static final String PRIVATE_MESSAGES_OFFLINE = "lang:private-messages.messages.offline";
    private static final String PRIVATE_MESSAGES_SELF = "lang:private-messages.messages.self";
    private static final String ANTI_SPAM_TOO_FAST = "lang:anti-spam.messages.too-fast";
    private static final String ANTI_SPAM_TOO_LONG = "lang:anti-spam.messages.too-long";
    private static final String ANTI_SPAM_TOO_MANY_CAPS = "lang:anti-spam.messages.too-many-caps";
    private static final String ANTI_SPAM_DUPLICATE = "lang:anti-spam.messages.duplicate";
    private static final String FILTER_DEFAULT_BLOCKED = "lang:filter.default-blocked-message";

    private final LanguageService languageService;

    public ChatConfigLoader() {
        this(null);
    }

    public ChatConfigLoader(LanguageService languageService) {
        this.languageService = languageService;
    }

    public ChatPluginConfig load(FileConfiguration config) {
        TargetMode targetMode = TargetMode.parse(config.getString("Options.Target", "ALL"));
        boolean autoJoin = config.getBoolean("Options.Auto-Join", true);
        boolean forceLegacy = config.getBoolean("Options.Force-Legacy", false);
        boolean debug = config.getBoolean("Options.Debug", false);
        boolean showChannelDisplay = config.getBoolean("Channels.Show-Display", false);
        String defaultChannelId = config.getString("Channels.Default", "global");
        ChannelSwitchSettings channelSwitchSettings = parseChannelSwitchSettings(config);
        List<ChatChannel> channels = parseChannels(config);
        CrossServerSettings crossServerSettings = parseCrossServer(config);
        AntiSpamSettings antiSpamSettings = parseAntiSpam(config);
        MentionSettings mentionSettings = parseMentionSettings(config);
        ColorChatSettings colorChatSettings = parseColorChatSettings(config);
        FixedColorSettings nameColorSettings = parseFixedColorSettings(config, "Name-Color.Fixed", FixedColorSettings.nameDefaults(), "name");
        MegaphoneSettings megaphoneSettings = new MegaphoneConfigParser(languageService).parse(config);
        PublicChatHighlightSettings publicChatHighlightSettings = parsePublicChatHighlightSettings(config);
        ItemShowcaseSettings itemShowcaseSettings = new ItemShowcaseConfigParser(languageService).parse(config);
        PrivateMessageSettings privateMessageSettings = parsePrivateMessageSettings(config);
        FilterSettings filterSettings = parseFilterSettings(config);

        List<FormatRule> formats = parseFormatRules(config.getList("Formats", List.of()));
        formats.addAll(parseFormatRules(config.getList("Channels.Formats", List.of())));

        if (formats.isEmpty()) {
            formats.add(FormatRule.fallback());
        }

        return new ChatPluginConfig(
            targetMode,
            autoJoin,
            forceLegacy,
            debug,
            showChannelDisplay,
            defaultChannelId,
            channelSwitchSettings,
            channels,
            crossServerSettings,
            antiSpamSettings,
            mentionSettings,
            colorChatSettings,
            nameColorSettings,
            megaphoneSettings,
            publicChatHighlightSettings,
            itemShowcaseSettings,
            privateMessageSettings,
            filterSettings,
            formats
        );
    }

    private FormatRule parseRule(Map<?, ?> raw) {
        String id = asString(raw.get("id"), "");
        String channel = asString(raw.get("channel"), "");
        String condition = asString(raw.get("condition"), "~");
        int priority = asInt(raw.get("priority"), 0);

        Map<?, ?> msgMap = asMap(raw.get("msg"));
        MessageOptions messageOptions = new MessageOptions(
            asString(msgMap.get("default-color"), "&f"),
            localizedString(msgMap.get("hover"), null),
            localizedString(msgMap.get("command"), null),
            localizedString(msgMap.get("suggest"), null),
            localizedString(msgMap.get("url"), null),
            localizedString(msgMap.get("copy"), null)
        );

        Map<?, ?> prefixMap = asMap(raw.get("prefix"));
        List<SectionStyle> prefixVariants = parseNamedSectionList(raw.get("prefix"));
        if (prefixVariants.isEmpty() && prefixMap.containsKey("world")) {
            prefixVariants = List.of(parseSection(asMap(prefixMap.get("world"))));
        }

        List<SectionStyle> nameVariants = parseNamedSectionList(raw.get("name"));
        if (nameVariants.isEmpty()) {
            nameVariants = parseSectionList(prefixMap.get("player"));
        }
        if (nameVariants.isEmpty()) {
            nameVariants = parseNamedSectionList(asMap(raw.get("player")));
        }
        if (nameVariants.isEmpty()) {
            nameVariants = List.of(SectionStyle.empty());
        }

        Map<?, ?> messageMap = asMap(raw.get("message"));
        Object messageSource = messageMap.containsKey("variants") ? messageMap.get("variants") : messageMap.get("text");
        List<SectionStyle> messageVariants = parseSectionList(messageSource);
        if (messageVariants.isEmpty()) {
            messageVariants = List.of(SectionStyle.messageFallback());
        }

        if (prefixVariants.isEmpty()) {
            prefixVariants = List.of(SectionStyle.empty());
        }

        return new FormatRule(id, channel, condition, priority, messageOptions, prefixVariants, nameVariants, messageVariants);
    }

    private CrossServerSettings parseCrossServer(FileConfiguration config) {
        CrossServerSettings defaults = CrossServerSettings.defaults();
        DatabaseSettings databaseDefaults = DatabaseSettings.defaults();
        CrossServerLogSettings logDefaults = defaults.logs();
        return new CrossServerSettings(
            config.getBoolean("Cross-Server.Enabled", defaults.enabled()),
            localizedConfigString(config, "Cross-Server.Server-Id", defaults.serverId()),
            localizedConfigString(config, "Cross-Server.Server-Name", defaults.serverName()),
            config.getInt("Cross-Server.Poll-Interval-Ticks", defaults.pollIntervalTicks()),
            config.getInt("Cross-Server.Batch-Size", defaults.batchSize()),
            config.getInt("Cross-Server.Retention-Hours", defaults.retentionHours()),
            config.getBoolean("Cross-Server.Show-Origin", defaults.showOrigin()),
            localizedConfigString(config, "Cross-Server.Origin-Format", defaults.originFormat()),
            new DatabaseSettings(
                config.getString("Cross-Server.Database.Host", databaseDefaults.host()),
                config.getInt("Cross-Server.Database.Port", databaseDefaults.port()),
                config.getString("Cross-Server.Database.Database", databaseDefaults.database()),
                config.getString("Cross-Server.Database.Schema", databaseDefaults.schema()),
                config.getString("Cross-Server.Database.Username", databaseDefaults.username()),
                config.getString("Cross-Server.Database.Password", databaseDefaults.password()),
                config.getString("Cross-Server.Database.Table", databaseDefaults.table()),
                config.getBoolean("Cross-Server.Database.SSL", databaseDefaults.ssl())
            ),
            new CrossServerLogSettings(
                config.getBoolean("Cross-Server.Logs.Enabled", logDefaults.enabled()),
                config.getString("Cross-Server.Logs.Permission", logDefaults.permission()),
                localizedConfigString(config, "Cross-Server.Logs.Default-Since", logDefaults.defaultSince()),
                config.getInt("Cross-Server.Logs.Default-Limit", logDefaults.defaultLimit()),
                config.getInt("Cross-Server.Logs.Max-Limit", logDefaults.maxLimit()),
                localizedConfigString(config, "Cross-Server.Logs.Timestamp-Format", logDefaults.timestampFormat()),
                localizedConfigString(config, "Cross-Server.Logs.Header-Format", logDefaults.headerFormat()),
                localizedConfigString(config, "Cross-Server.Logs.Line-Format", logDefaults.lineFormat()),
                localizedConfigStringList(config, "Cross-Server.Logs.Hover-Format", logDefaults.hoverFormat()),
                localizedConfigString(config, "Cross-Server.Logs.Footer-Format", logDefaults.footerFormat())
            )
        );
    }

    private PrivateMessageSettings parsePrivateMessageSettings(FileConfiguration config) {
        PrivateMessageSettings defaults = PrivateMessageSettings.defaults();
        List<PrivateMessageRule> rules = new ArrayList<>();
        for (Object rawRule : config.getList("Private-Messages.Rules", List.of())) {
            if (rawRule instanceof Map<?, ?> map) {
                rules.add(new PrivateMessageRule(
                    asString(map.get("id"), ""),
                    asString(map.get("condition"), "~"),
                    asString(map.get("target-condition"), "~"),
                    asInt(map.get("priority"), 0),
                    localizedString(map.get("sender"), defaults.senderFormat()),
                    localizedString(map.get("receiver"), defaults.receiverFormat()),
                    localizedString(map.get("spy"), defaults.spyFormat())
                ));
            }
        }
        return new PrivateMessageSettings(
            config.getBoolean("Private-Messages.Enabled", defaults.enabled()),
            localizedConfigString(config, "Private-Messages.Format.Sender", defaults.senderFormat()),
            localizedConfigString(config, "Private-Messages.Format.Receiver", defaults.receiverFormat()),
            localizedConfigString(config, "Private-Messages.Format.Spy", defaults.spyFormat()),
            localizedConfigString(config, "Private-Messages.Messages.Disabled", PRIVATE_MESSAGES_DISABLED),
            localizedConfigString(config, "Private-Messages.Messages.No-Reply-Target", PRIVATE_MESSAGES_NO_REPLY_TARGET),
            localizedConfigString(config, "Private-Messages.Messages.Offline", PRIVATE_MESSAGES_OFFLINE),
            localizedConfigString(config, "Private-Messages.Messages.Self", PRIVATE_MESSAGES_SELF),
            rules
        );
    }

    private List<FormatRule> parseFormatRules(List<?> rawFormats) {
        List<FormatRule> formats = new ArrayList<>();
        for (Object rawFormat : rawFormats == null ? List.of() : rawFormats) {
            if (rawFormat instanceof Map<?, ?> formatMap) {
                formats.add(parseRule(formatMap));
            }
        }
        return formats;
    }

    private FilterSettings parseFilterSettings(FileConfiguration config) {
        FilterSettings defaults = FilterSettings.defaults();
        List<FilterRule> rules = new ArrayList<>();
        for (Object rawRule : config.getList("Filter.Rules", List.of())) {
            if (rawRule instanceof Map<?, ?> map) {
                rules.add(new FilterRule(
                    asString(map.get("id"), ""),
                    asString(map.get("scope"), "all"),
                    asString(map.get("mode"), "replace"),
                    asString(map.get("match"), ""),
                    asBoolean(map.get("regex"), false),
                    asBoolean(map.get("case-sensitive"), false),
                    localizedString(map.get("replacement"), "***"),
                    localizedString(map.get("message"), FILTER_DEFAULT_BLOCKED),
                    asString(map.get("bypass-permission"), "ymchat.filter.bypass"),
                    rawStringList(map.get("channels"))
                ));
            }
        }
        return new FilterSettings(
            config.getBoolean("Filter.Enabled", defaults.enabled()),
            parseFilterCloudSettings(config),
            rules
        );
    }

    private FilterCloudSettings parseFilterCloudSettings(FileConfiguration config) {
        FilterCloudSettings defaults = FilterCloudSettings.defaults();
        return new FilterCloudSettings(
            config.getBoolean("Filter.Cloud.Enabled", defaults.enabled()),
            config.getString("Filter.Cloud.Url", defaults.url()),
            config.getString("Filter.Cloud.Array-Path", defaults.arrayPath()),
            config.getLong("Filter.Cloud.Refresh-Minutes", defaults.refreshMinutes()),
            config.getString("Filter.Cloud.Mode", defaults.mode()),
            localizedConfigString(config, "Filter.Cloud.Replacement", defaults.replacement()),
            localizedConfigString(config, "Filter.Cloud.Message", FILTER_DEFAULT_BLOCKED),
            config.getString("Filter.Cloud.Bypass-Permission", defaults.bypassPermission()),
            config.getStringList("Filter.Cloud.Scopes").isEmpty()
                ? defaults.scopes()
                : config.getStringList("Filter.Cloud.Scopes")
        );
    }

    private List<ChatChannel> parseChannels(FileConfiguration config) {
        List<ChatChannel> channels = new ArrayList<>();
        for (Object rawChannel : config.getList("Channels.List", List.of())) {
            if (rawChannel instanceof Map<?, ?> map) {
                String id = asString(map.get("id"), "").trim().toLowerCase();
                if (id.isBlank()) {
                    continue;
                }
                String format = asString(map.get("format"), "").trim();
                if (format.isBlank()) {
                    format = "default";
                }
                channels.add(new ChatChannel(
                    id,
                    localizedString(map.get("display"), ""),
                    TargetMode.parse(asString(map.get("target"), "ALL")),
                    asString(map.get("permission"), ""),
                    format,
                    asBoolean(map.get("cross-server"), false),
                    rawStringList(map.get("aliases"))
                ));
            }
        }

        boolean usedFallbackChannels = channels.isEmpty();
        if (usedFallbackChannels) {
            channels.add(new ChatChannel("global", "&#777777[&#33CCFFGlobal&#777777] ", TargetMode.ALL, "", "default", false, List.of("global", "g")));
            channels.add(new ChatChannel("cross-server", "&#777777[&#33CCFFCross-Server&#777777] ", TargetMode.ALL, "", "cross-server", true, List.of("cross", "cf")));
            channels.add(new ChatChannel("world", "&#777777[&#FFB833World&#777777] ", TargetMode.ALL, "", "world", false, List.of("world", "w")));
            channels.add(new ChatChannel("staff", "&#777777[&#FF6B6BStaff&#777777] ", TargetMode.ALL, "ymchat.channel.staff", "staff", false, List.of("staff", "s")));
        }
        return channels;
    }

    private ChannelSwitchSettings parseChannelSwitchSettings(FileConfiguration config) {
        ChannelSwitchSettings defaults = ChannelSwitchSettings.defaults();
        return new ChannelSwitchSettings(
            config.getBoolean("Channels.Switch.Enabled", defaults.enabled()),
            config.getString("Channels.Switch.Admin-Permission", defaults.adminPermission()),
            config.getBoolean("Channels.Switch.Cross-Server-Admin-Only", defaults.crossServerAdminOnly())
        );
    }

    private AntiSpamSettings parseAntiSpam(FileConfiguration config) {
        AntiSpamSettings defaults = AntiSpamSettings.defaults();
        return new AntiSpamSettings(
            config.getBoolean("Anti-Spam.Enabled", defaults.enabled()),
            config.getString("Anti-Spam.Bypass-Permission", defaults.bypassPermission()),
            config.getLong("Anti-Spam.Cooldown-Millis", defaults.cooldownMillis()),
            config.getInt("Anti-Spam.Max-Length", defaults.maxLength()),
            config.getDouble("Anti-Spam.Caps-Ratio", defaults.capsRatio()),
            config.getInt("Anti-Spam.Caps-Min-Letters", defaults.capsMinLetters()),
            config.getLong("Anti-Spam.Duplicate-Window-Millis", defaults.duplicateWindowMillis()),
            config.getBoolean("Anti-Spam.Block-Duplicate", defaults.blockDuplicate()),
            localizedConfigString(config, "Anti-Spam.Messages.Too-Fast", ANTI_SPAM_TOO_FAST),
            localizedConfigString(config, "Anti-Spam.Messages.Too-Long", ANTI_SPAM_TOO_LONG),
            localizedConfigString(config, "Anti-Spam.Messages.Too-Many-Caps", ANTI_SPAM_TOO_MANY_CAPS),
            localizedConfigString(config, "Anti-Spam.Messages.Duplicate", ANTI_SPAM_DUPLICATE)
        );
    }

    private MentionSettings parseMentionSettings(FileConfiguration config) {
        MentionSettings defaults = MentionSettings.defaults();
        return new MentionSettings(
            config.getBoolean("Mentions.Enabled", defaults.enabled()),
            config.getString("Mentions.Prefix", defaults.prefix()),
            config.getString("Mentions.Highlight-Color", defaults.highlightColor()),
            config.getString("Mentions.Sound", defaults.sound()),
            config.getBoolean("Mentions.Notify-Actionbar", defaults.notifyActionbar()),
            config.getBoolean("Mentions.Allow-Everyone", defaults.allowEveryone()),
            config.getString("Mentions.Everyone-Permission", defaults.everyonePermission()),
            config.getString("Mentions.Everyone-Token", defaults.everyoneToken()),
            config.getBoolean("Mentions.Match-Plain-Names", defaults.matchPlainNames())
        );
    }

    private ColorChatSettings parseColorChatSettings(FileConfiguration config) {
        ColorChatSettings defaults = ColorChatSettings.defaults();
        InlineColorSettings inlineDefaults = defaults.inlineSettings();
        FixedColorSettings fixedDefaults = defaults.fixedSettings();
        FixedColorSettings fixedSettings = parseFixedColorSettings(config, "Color-Chat.Fixed", fixedDefaults, "chat");
        return new ColorChatSettings(
            new InlineColorSettings(
                config.getString("Color-Chat.Inline.legacy-permission", inlineDefaults.legacyPermission()),
                config.getString("Color-Chat.Inline.format-permission", inlineDefaults.formatPermission()),
                config.getString("Color-Chat.Inline.rgb-permission", inlineDefaults.rgbPermission())
            ),
            fixedSettings
        );
    }

    private FixedColorSettings parseFixedColorSettings(
        FileConfiguration config,
        String path,
        FixedColorSettings defaults,
        String scopeKey
    ) {
        List<ColorPreset> rgbColors = parseScopedRgbColors(config, path + ".rgb-colors");
        if (rgbColors.isEmpty()) {
            rgbColors = parseSharedRgbColors(config, scopeKey);
        }
        if (rgbColors.isEmpty()) {
            rgbColors = defaults.rgbColors().stream()
                .map(color -> new ColorPreset(
                    color.id(),
                    localizedString(color.display(), color.display()),
                    color.permission(),
                    color.value(),
                    color.gradientColors()
                ))
                .toList();
        }
        return new FixedColorSettings(
            config.getBoolean(path + ".enabled", defaults.enabled()),
            rgbColors
        );
    }

    private List<ColorPreset> parseScopedRgbColors(FileConfiguration config, String path) {
        List<ColorPreset> rgbColors = new ArrayList<>();
        for (Object rawPreset : config.getList(path, List.of())) {
            if (rawPreset instanceof Map<?, ?> map) {
                ColorPreset preset = parseColorPreset(map, "permission", "");
                if (preset != null) {
                    rgbColors.add(preset);
                }
            }
        }
        return rgbColors;
    }

    private List<ColorPreset> parseSharedRgbColors(FileConfiguration config, String scopeKey) {
        List<?> rawColors = config.getList("Colors.rgb-colors", List.of());
        if (rawColors.isEmpty()) {
            rawColors = config.getList("Colors.Fixed.rgb-colors", List.of());
        }

        List<ColorPreset> rgbColors = new ArrayList<>();
        for (Object rawPreset : rawColors) {
            if (rawPreset instanceof Map<?, ?> map) {
                ColorPreset preset = parseColorPreset(map, scopeKey + "-permission", defaultRgbPermission(scopeKey, asString(map.get("id"), "")));
                if (preset != null) {
                    rgbColors.add(preset);
                }
            }
        }
        return rgbColors;
    }

    private ColorPreset parseColorPreset(Map<?, ?> map, String permissionKey, String defaultPermission) {
        String id = asString(map.get("id"), "").trim();
        if (id.isBlank()) {
            return null;
        }
        String scopedKey = permissionKey.endsWith("-permission")
            ? permissionKey.substring(0, permissionKey.length() - "-permission".length())
            : "";
        Object display = firstPresent(scopedKey.isBlank() ? null : map.get(scopedKey + "-display"), map.get("display"));
        return new ColorPreset(
            id,
            localizedString(display, ""),
            firstNonBlank(
                asString(map.get(permissionKey), ""),
                asString(asMap(map.get("permissions")).get(scopedKey), ""),
                defaultPermission
            ),
            asString(map.get("value"), ""),
            parseGradientColors(map)
        );
    }

    private List<String> parseGradientColors(Map<?, ?> map) {
        Object gradient = map.get("gradient");
        if (gradient instanceof Map<?, ?> gradientMap) {
            return rawStringList(gradientMap.get("colors"));
        }
        if (gradient instanceof List<?>) {
            return rawStringList(gradient);
        }
        List<String> colors = rawStringList(map.get("gradient-colors"));
        if (!colors.isEmpty()) {
            return colors;
        }
        return rawStringList(map.get("colors"));
    }

    private String defaultRgbPermission(String scopeKey, String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        return "name".equalsIgnoreCase(scopeKey)
            ? "ymchat.namecolor.rgb." + id
            : "ymchat.color.rgb." + id;
    }

    private Object firstPresent(Object first, Object second) {
        return first == null ? second : first;
    }

    private String firstNonBlank(String first, String second, String third) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return third == null ? "" : third;
    }

    private PublicChatHighlightSettings parsePublicChatHighlightSettings(FileConfiguration config) {
        PublicChatHighlightSettings defaults = PublicChatHighlightSettings.defaults();
        List<String> defaultChannels = config.getStringList("Highlights.Default-Channels");
        if (defaultChannels.isEmpty()) {
            defaultChannels = defaults.defaultChannels();
        }

        List<KeywordHighlightRule> keywordRules = new ArrayList<>();
        for (Object rawRule : config.getList("Highlights.Keyword-Rules", List.of())) {
            if (rawRule instanceof Map<?, ?> map) {
                keywordRules.add(new KeywordHighlightRule(
                    asString(map.get("id"), ""),
                    asBoolean(map.get("enabled"), true),
                    asInt(map.get("priority"), 0),
                    rawStringList(map.get("channels")),
                    asString(map.get("type"), "literal"),
                    asString(map.get("match"), ""),
                    rawStringList(map.get("matches")),
                    asBoolean(map.get("case-sensitive"), false),
                    asBoolean(map.get("whole-word"), false),
                    asString(map.get("color"), ""),
                    rawStringList(map.get("formats")),
                    rawStringList(map.get("hover")),
                    asString(map.get("suggest"), ""),
                    asString(map.get("command"), ""),
                    asString(map.get("copy"), "")
                ));
            }
        }
        if (keywordRules.isEmpty()) {
            keywordRules = defaults.keywordRules();
        }

        List<PatternHighlightRule> patternRules = new ArrayList<>();
        var patternSection = config.getConfigurationSection("Highlights.Pattern-Rules");
        if (patternSection != null) {
            for (String id : patternSection.getKeys(false)) {
                patternRules.add(new PatternHighlightRule(
                    id,
                    patternSection.getBoolean(id + ".enabled", true),
                    patternSection.getInt(id + ".priority", 0),
                    patternSection.getStringList(id + ".channels"),
                    patternSection.getStringList(id + ".regexes"),
                    patternSection.getString(id + ".color", ""),
                    patternSection.getStringList(id + ".formats"),
                    patternSection.getStringList(id + ".hover"),
                    patternSection.getString(id + ".suggest", ""),
                    patternSection.getString(id + ".command", ""),
                    patternSection.getString(id + ".copy", "")
                ));
            }
        }
        if (patternRules.isEmpty()) {
            patternRules = defaults.patternRules();
        }

        return new PublicChatHighlightSettings(
            config.getBoolean("Highlights.Enabled", defaults.enabled()),
            defaultChannels,
            keywordRules,
            patternRules
        );
    }

    private List<SectionStyle> parseNamedSectionList(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            if (map.containsKey("variants")) {
                return parseSectionList(map.get("variants"));
            }
            if (map.containsKey("text") && map.get("text") instanceof List<?>) {
                return parseSectionList(map.get("text"));
            }
            if (looksLikeSectionDefinition(map)) {
                return List.of(parseSection(map));
            }
            return List.of();
        }
        return parseSectionList(raw);
    }

    private List<SectionStyle> parseSectionList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }

        List<SectionStyle> styles = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                styles.add(parseSection(map));
            } else if (entry != null) {
                styles.add(parseInlineSection(entry.toString()));
            }
        }
        return styles;
    }

    private SectionStyle parseSection(Map<?, ?> raw) {
        if (raw.isEmpty()) {
            return SectionStyle.empty();
        }
        return new SectionStyle(
            asString(raw.get("condition"), "~"),
            localizedString(raw.get("text"), ""),
            localizedString(raw.get("hover"), null),
            localizedString(raw.get("command"), null),
            localizedString(raw.get("suggest"), null),
            localizedString(raw.get("url"), null),
            localizedString(raw.get("copy"), null)
        );
    }

    private SectionStyle parseInlineSection(String raw) {
        String condition = "~";
        String text = raw;

        Matcher matcher = INLINE_CONDITION.matcher(raw);
        if (matcher.find()) {
            condition = matcher.group(1).trim();
            text = raw.substring(0, matcher.start()).trim();
        }

        return new SectionStyle(condition, localizedString(text, ""), null, null, null, null, null);
    }

    private boolean looksLikeSectionDefinition(Map<?, ?> map) {
        return map.containsKey("text")
            || map.containsKey("hover")
            || map.containsKey("command")
            || map.containsKey("suggest")
            || map.containsKey("url")
            || map.containsKey("copy");
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private List<String> rawStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object element : list) {
            if (element != null) {
                result.add(element.toString());
            }
        }
        return result;
    }

    private String localizedConfigString(FileConfiguration config, String path, String fallback) {
        return localizedString(config.getString(path, fallback), fallback);
    }

    private List<String> localizedConfigStringList(FileConfiguration config, String path, List<String> fallback) {
        List<String> configured = config.getStringList(path);
        if (configured.isEmpty()) {
            configured = fallback == null ? List.of() : fallback;
        }
        return languageService == null ? configured : languageService.resolveConfiguredList(configured);
    }

    private String localizedString(Object value, String fallback) {
        String raw = asString(value, fallback);
        return languageService == null ? raw : languageService.resolveConfigured(raw);
    }

    private String asString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return fallback;
    }
}
