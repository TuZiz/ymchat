package ym.ymchat.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.color.ColorPreset;
import ym.ymchat.config.color.FixedColorSettings;
import ym.ymchat.service.color.ColorScope;
import ym.ymchat.service.color.PlayerColorPreference;
import ym.ymchat.service.color.PlayerColorService;

public final class YmChatCommand implements CommandExecutor, TabCompleter {

    private static final String RELOAD_ZH = "\u91cd\u8f7d";
    private static final String JOIN_ZH = "\u52a0\u5165";
    private static final String LEAVE_ZH = "\u79bb\u5f00";
    private static final String STATUS_ZH = "\u72b6\u6001";
    private static final String CHANNEL_ZH = "\u9891\u9053";
    private static final String DEBUG_ZH_NAME = "\u8c03\u8bd5";
    private static final String COLOR_ZH = "\u989c\u8272";
    private static final String NAME_COLOR_ZH = "\u540d\u5b57\u989c\u8272";
    private static final String MEGAPHONE_ZH = "\u5587\u53ed";
    private static final String LOGS_ZH = "\u65e5\u5fd7";

    private static final List<String> SUBCOMMANDS_LEGACY = List.of(
        "reload",
        "join",
        "leave",
        "status",
        "channel",
        "debug",
        "color",
        "namecolor",
        "megaphone",
        "logs"
    );
    private static final List<String> DEBUG_ACTIONS_LEGACY = List.of("on", "off");
    private static final List<String> COLOR_ACTIONS_LEGACY = List.of("off", "reset");
    private static final List<String> LOG_KEYS_LEGACY = List.of("help", "player:", "channel:", "keyword:", "since:", "page:", "limit:");

    private final YmChatPlugin plugin;

    public YmChatCommand(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandMessages.requirePermission(plugin, sender, "ymchat.use")) {
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = normalize(args[0]);
        if (matches(sub, "reload", RELOAD_ZH)) {
            return handleReload(sender);
        }
        if (matches(sub, "join", JOIN_ZH)) {
            return handleJoin(sender);
        }
        if (matches(sub, "leave", LEAVE_ZH)) {
            return handleLeave(sender);
        }
        if (matches(sub, "status", STATUS_ZH)) {
            return handleStatus(sender);
        }
        if (matches(sub, "channel", CHANNEL_ZH)) {
            return handleChannel(sender, args);
        }
        if (matches(sub, "debug", DEBUG_ZH_NAME)) {
            return handleDebug(sender, args);
        }
        if (matches(sub, "color", COLOR_ZH)) {
            return handleColor(sender, args);
        }
        if (matches(sub, "namecolor", NAME_COLOR_ZH)) {
            return handleNameColor(sender, args);
        }
        if (matches(sub, "megaphone", MEGAPHONE_ZH, "\u5927\u5587\u53ed", "\u558a\u8bdd")) {
            return handleMegaphone(sender, args);
        }
        if (matches(sub, "logs", LOGS_ZH)) {
            return handleLogs(sender, args);
        }
        if (matches(sub, "showcase")) {
            return handleShowcase(sender, args);
        }
        sendHelp(sender);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("ymchat.reload")) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.reload.no-permission");
            return true;
        }
        plugin.reloadChatSettings();
        CommandMessages.sendKey(plugin, sender, "commands.ymchat.reload.success");
        return true;
    }

    private boolean handleJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.join.only-player");
            return true;
        }
        plugin.join(player);
        CommandMessages.sendKey(plugin, sender, "commands.ymchat.join.success");
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.leave.only-player");
            return true;
        }
        plugin.leave(player);
        CommandMessages.sendKey(plugin, sender, "commands.ymchat.leave.success");
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.auto-join", "value", state(plugin.getChatConfig().autoJoin()));
        CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.force-legacy", "value", state(plugin.getChatConfig().forceLegacy()));
        CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.global-debug", "value", state(plugin.getChatConfig().debug()));
        CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.language", "value", plugin.getLanguageService().activeLocale());
        CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.channel-switch", "value", describeChannelSwitch());
        CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.cross-server-sync", "value", describeCrossServerSync());
        if (sender instanceof Player player) {
            ChatChannel channel = plugin.getChannelService().resolveChannel(player);
            PlayerColorService.ResolvedColor resolved = resolveColor(player, channel);
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.participating", "value", state(plugin.isParticipating(player)));
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.channel", "value", plugin.getChannelService().describeChannel(channel));
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.channel-cross-server", "value", state(channel.crossServer()));
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.reply-target", "value", plugin.getPrivateMessageService().describeReplyTarget(player));
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.personal-debug", "value", state(plugin.getDebugService().isDebugEnabled(player)));
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.socialspy", "value", state(plugin.getPrivateMessageService().isSocialSpyEnabled(player)));
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.chat-color", "value", describeCurrentColor(resolved, "commands.ymchat.color"));
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.status.saved-mode", "value", describeStoredMode(resolved.preference(), "commands.ymchat.color"));
        }
        return true;
    }

    private boolean handleChannel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.channel.only-player");
            return true;
        }
        if (args.length == 1) {
            CommandMessages.sendKey(
                plugin,
                sender,
                "commands.ymchat.channel.current",
                "channel", plugin.getChannelService().describeChannel(plugin.getChannelService().resolveChannel(player))
            );
            CommandMessages.sendKey(
                plugin,
                sender,
                "commands.ymchat.channel.available",
                "channels", String.join("&#AAAAAA, &#FFFFFF", plugin.getChannelService().availableChannelIds(player))
            );
            return true;
        }
        CommandMessages.send(sender, plugin.getChannelService().switchChannel(player, args[1]));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.debug.global-status", "value", state(plugin.getChatConfig().debug()));
            return true;
        }
        boolean enabled = args.length >= 2
            ? parseToggle(args[1], !plugin.getDebugService().isDebugEnabled(player))
            : !plugin.getDebugService().isDebugEnabled(player);
        plugin.getDebugService().setDebug(player, enabled);
        CommandMessages.sendKey(
            plugin,
            sender,
            "commands.ymchat.debug.toggle-status",
            "value", plugin.getLanguageService().get(enabled ? "common.state.enabled" : "common.state.disabled")
        );
        return true;
    }

    private boolean handleColor(CommandSender sender, String[] args) {
        return handleColor(
            sender,
            args,
            ColorScope.CHAT,
            plugin.getChatConfig().colorChatSettings().fixedSettings(),
            "commands.ymchat.color",
            "&f"
        );
    }

    private boolean handleNameColor(CommandSender sender, String[] args) {
        return handleColor(
            sender,
            args,
            ColorScope.NAME,
            plugin.getChatConfig().nameColorSettings(),
            "commands.ymchat.namecolor",
            "&f"
        );
    }

    private boolean handleColor(
        CommandSender sender,
        String[] args,
        ColorScope scope,
        FixedColorSettings fixedSettings,
        String messageRoot,
        String defaultColor
    ) {
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, messageRoot + ".only-player");
            return true;
        }

        if (fixedSettings == null || !fixedSettings.enabled()) {
            CommandMessages.sendKey(plugin, sender, messageRoot + ".fixed-disabled");
            return true;
        }
        if (!plugin.getPlayerColorService().canUseCommands(player, scope, fixedSettings)) {
            CommandMessages.sendKey(plugin, sender, messageRoot + ".no-permission");
            return true;
        }

        if (args.length == 1) {
            CommandMessages.sendKey(plugin, player, messageRoot + ".usage");
            sendColorOverview(player, scope, fixedSettings, messageRoot, defaultColor);
            return true;
        }

        String action = normalize(args[1]);
        if (matches(action, "gui", "\u83dc\u5355")) {
            CommandMessages.sendKey(plugin, player, messageRoot + ".menu-removed");
            CommandMessages.sendKey(plugin, player, messageRoot + ".usage");
            sendColorOverview(player, scope, fixedSettings, messageRoot, defaultColor);
            return true;
        }

        if (matches(action, "off", "\u5173\u95ed", "\u5173")) {
            plugin.getPlayerColorService().setOff(player, scope);
            CommandMessages.sendKey(plugin, player, messageRoot + ".off");
            return true;
        }
        if (matches(action, "reset", "\u91cd\u7f6e", "\u6e05\u9664")) {
            plugin.getPlayerColorService().reset(player, scope);
            CommandMessages.sendKey(plugin, player, messageRoot + ".reset");
            return true;
        }

        ColorPreset rgbColor = fixedSettings.findRgbColor(action);
        if (rgbColor == null) {
            CommandMessages.sendKey(plugin, player, messageRoot + ".unknown", "color", action);
            return true;
        }
        if (!plugin.getPlayerColorService().setRgb(player, scope, fixedSettings, action)) {
            CommandMessages.sendKey(plugin, player, messageRoot + ".no-permission-rgb", "color", action);
            return true;
        }
        CommandMessages.sendKey(plugin, player, messageRoot + ".switched-rgb", "color", rgbColor.display());
        return true;
    }

    private boolean handleMegaphone(CommandSender sender, String[] args) {
        String[] megaphoneArgs = args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
        return plugin.getMegaphoneService().handleCommand(sender, megaphoneArgs);
    }

    private boolean handleLogs(CommandSender sender, String[] args) {
        String[] logArgs = args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
        return plugin.getCrossServerLogService().handleLogs(sender, logArgs);
    }

    private boolean handleShowcase(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, "commands.ymchat.showcase.only-player");
            return true;
        }
        if (args.length != 4 || !"open".equalsIgnoreCase(args[1])) {
            CommandMessages.sendKey(plugin, player, "commands.ymchat.showcase.invalid");
            return true;
        }
        plugin.getChatShowcaseSnapshotService().openSnapshot(player, args[2], args[3]);
        return true;
    }

    private void sendColorOverview(
        Player player,
        ColorScope scope,
        FixedColorSettings fixedSettings,
        String messageRoot,
        String defaultColor
    ) {
        PlayerColorService.ResolvedColor resolved = resolveColor(player, scope, defaultColor);
        List<ColorPreset> rgbColors = plugin.getPlayerColorService().availableRgbColors(player, scope, fixedSettings);
        CommandMessages.sendKey(plugin, player, messageRoot + ".overview.active", "value", describeCurrentColor(resolved, messageRoot));
        CommandMessages.sendKey(plugin, player, messageRoot + ".overview.saved-mode", "value", describeStoredMode(resolved.preference(), messageRoot));
        CommandMessages.sendKey(
            plugin,
            player,
            messageRoot + ".overview.rgb-colors",
            "value", rgbColors.isEmpty()
                ? plugin.getLanguageService().get("common.none")
                : String.join("&#AAAAAA, &#FFFFFF", rgbColors.stream().map(ColorPreset::id).toList())
        );
    }

    private PlayerColorService.ResolvedColor resolveColor(Player player, ChatChannel channel) {
        return resolveColor(
            player,
            ColorScope.CHAT,
            plugin.getChatConfig().firstMatching(player, channel.id(), channel.format()).messageOptions().defaultColor()
        );
    }

    private PlayerColorService.ResolvedColor resolveColor(Player player, ColorScope scope, String defaultColor) {
        if (scope == ColorScope.NAME) {
            return plugin.getPlayerColorService().resolve(
                player,
                ColorScope.NAME,
                plugin.getChatConfig().nameColorSettings(),
                defaultColor
            );
        }
        ChatChannel channel = plugin.getChannelService().resolveChannel(player);
        return plugin.getPlayerColorService().resolve(
            player,
            plugin.getChatConfig().colorChatSettings(),
            plugin.getChatConfig().firstMatching(player, channel.id(), channel.format()).messageOptions().defaultColor()
        );
    }

    private String describeCurrentColor(PlayerColorService.ResolvedColor resolved, String messageRoot) {
        return switch (resolved.source()) {
            case MANUAL_LEGACY -> plugin.getLanguageService().get(
                messageRoot + ".current.manual-legacy",
                "value", legacyColorDisplay(resolved.preference().value())
            );
            case MANUAL_RGB -> plugin.getLanguageService().get(
                messageRoot + ".current.manual-rgb",
                "value", resolved.rgbColor() == null ? resolved.baseColorValue() : resolved.rgbColor().display()
            );
            case MANUAL_OFF -> plugin.getLanguageService().get(messageRoot + ".current.manual-off");
            case RULE_DEFAULT -> plugin.getLanguageService().get(messageRoot + ".current.rule-default");
        };
    }

    private String legacyColorDisplay(String code) {
        String normalized = code == null || code.isBlank() ? "f" : code.trim().substring(0, 1).toLowerCase(Locale.ROOT);
        return "&" + normalized + normalized.toUpperCase(Locale.ROOT) + "&r";
    }

    private String describeStoredMode(PlayerColorPreference preference, String messageRoot) {
        if (preference == null) {
            return plugin.getLanguageService().get(messageRoot + ".mode.none");
        }
        return switch (preference.mode()) {
            case LEGACY -> plugin.getLanguageService().get(messageRoot + ".mode.legacy", "value", preference.value());
            case PRESET -> plugin.getLanguageService().get(messageRoot + ".mode.preset", "value", preference.value());
            case RGB -> plugin.getLanguageService().get(messageRoot + ".mode.rgb", "value", preference.value());
            case OFF -> plugin.getLanguageService().get(messageRoot + ".mode.off");
        };
    }

    private String state(boolean enabled) {
        return plugin.getLanguageService().get(enabled ? "common.state.enabled" : "common.state.disabled");
    }

    private String describeChannelSwitch() {
        if (plugin.getChatConfig().channelSwitchSettings().enabled()) {
            return state(true);
        }
        return plugin.getLanguageService().get(
            "commands.ymchat.status.channel-switch-admin-only",
            "permission", plugin.getChatConfig().channelSwitchSettings().adminPermission()
        );
    }

    private String describeCrossServerSync() {
        String status = plugin.getCrossServerChatService().describeStatus();
        if ("disabled".equalsIgnoreCase(status)) {
            return plugin.getLanguageService().get("commands.ymchat.status.cross-server-disabled");
        }
        if ("misconfigured".equalsIgnoreCase(status)) {
            return plugin.getLanguageService().get("commands.ymchat.status.cross-server-misconfigured");
        }
        return plugin.getLanguageService().get("commands.ymchat.status.cross-server-enabled", "value", status);
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.getLanguageService().getList("commands.ymchat.help")) {
            CommandMessages.send(sender, line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS_LEGACY, args[0]);
        }
        if (args.length == 2 && matches(normalize(args[0]), "channel", CHANNEL_ZH) && sender instanceof Player player) {
            return filter(plugin.getChannelService().availableChannelIds(player), args[1]);
        }
        if (args.length == 2 && matches(normalize(args[0]), "debug", DEBUG_ZH_NAME)) {
            return filter(DEBUG_ACTIONS_LEGACY, args[1]);
        }
        if (args.length == 2
            && matches(normalize(args[0]), "color", COLOR_ZH)
            && sender instanceof Player player
            && plugin != null) {
            LinkedHashSet<String> values = new LinkedHashSet<>(COLOR_ACTIONS_LEGACY);
            values.addAll(plugin.getPlayerColorService().availableRgbColors(player, plugin.getChatConfig().colorChatSettings())
                .stream()
                .map(ColorPreset::id)
                .toList());
            return filter(values, args[1]);
        }
        if (args.length == 2
            && matches(normalize(args[0]), "namecolor", NAME_COLOR_ZH)
            && sender instanceof Player player
            && plugin != null) {
            LinkedHashSet<String> values = new LinkedHashSet<>(COLOR_ACTIONS_LEGACY);
            FixedColorSettings fixedSettings = plugin.getChatConfig().nameColorSettings();
            values.addAll(plugin.getPlayerColorService().availableRgbColors(player, ColorScope.NAME, fixedSettings)
                .stream()
                .map(ColorPreset::id)
                .toList());
            return filter(values, args[1]);
        }
        if (args.length >= 2 && matches(normalize(args[0]), "megaphone", MEGAPHONE_ZH, "\u5927\u5587\u53ed", "\u558a\u8bdd")) {
            if (plugin == null) {
                return List.of();
            }
            return plugin.getMegaphoneService().tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length >= 2 && matches(normalize(args[0]), "logs", LOGS_ZH)) {
            String current = args[args.length - 1];
            if (args.length == 2) {
                return filter(LOG_KEYS_LEGACY, current);
            }
            if (current.startsWith("channel:") || current.startsWith("\u9891\u9053:")) {
                String prefix = current.startsWith("\u9891\u9053:") ? "\u9891\u9053:" : "channel:";
                return filter(
                    plugin.getChatConfig().channels().stream().map(channel -> prefix + plugin.getChannelService().describeChannel(channel)).toList(),
                    current
                );
            }
            if (current.startsWith("since:") || current.startsWith("\u65f6\u95f4:")) {
                String prefix = current.startsWith("\u65f6\u95f4:") ? "\u65f6\u95f4:" : "since:";
                return filter(List.of(prefix + "30m", prefix + "1h", prefix + "6h", prefix + "24h", prefix + "7d"), current);
            }
            if (current.startsWith("page:") || current.startsWith("\u9875:")) {
                String prefix = current.startsWith("\u9875:") ? "\u9875:" : "page:";
                return filter(List.of(prefix + "1", prefix + "2", prefix + "3"), current);
            }
            if (current.startsWith("limit:") || current.startsWith("\u6570\u91cf:")) {
                String prefix = current.startsWith("\u6570\u91cf:") ? "\u6570\u91cf:" : "limit:";
                return filter(List.of(prefix + "10", prefix + "20", prefix + "30"), current);
            }
            return filter(LOG_KEYS_LEGACY.subList(1, LOG_KEYS_LEGACY.size()), current);
        }
        if ("showcase".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filter(List.of("open"), args[1]);
            }
            if (args.length == 3 && "open".equalsIgnoreCase(args[1])) {
                return filter(List.of("inventory", "ender-chest"), args[2]);
            }
        }
        return List.of();
    }

    private List<String> filter(Iterable<String> source, String input) {
        List<String> result = new ArrayList<>();
        String lowered = normalize(input);
        for (String value : source) {
            if (normalize(value).startsWith(lowered)) {
                result.add(value);
            }
        }
        return result;
    }

    private boolean parseToggle(String input, boolean fallback) {
        String normalized = normalize(input);
        if (matches(normalized, "on", "true", "enable", "\u5f00\u542f", "\u6253\u5f00", "\u542f\u7528", "\u5f00")) {
            return true;
        }
        if (matches(normalized, "off", "false", "disable", "\u5173\u95ed", "\u5173", "\u7981\u7528")) {
            return false;
        }
        return fallback;
    }

    private boolean matches(String input, String... values) {
        for (String value : values) {
            if (normalize(value).equals(input)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }
}
