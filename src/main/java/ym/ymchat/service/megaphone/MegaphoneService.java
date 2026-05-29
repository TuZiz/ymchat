package ym.ymchat.service.megaphone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.megaphone.MegaphoneMode;
import ym.ymchat.config.megaphone.MegaphoneSettings;
import ym.ymchat.service.color.ColorGradientUtil;
import ym.ymchat.service.color.ColorScope;
import ym.ymchat.service.color.PlayerColorService;
import ym.ymchat.service.text.PlaceholderResolver;
import ym.ymchat.service.text.RichText;

public final class MegaphoneService {

    private static final String MESSAGE_TOKEN_PLACEHOLDER = "__YMCHAT_MEGAPHONE_MESSAGE__";

    private final YmChatPlugin plugin;
    private final MegaphoneBalanceStore balanceStore;
    private MegaphoneSettings settings = MegaphoneSettings.defaults();

    public MegaphoneService(YmChatPlugin plugin, MegaphoneBalanceStore balanceStore) {
        this.plugin = plugin;
        this.balanceStore = balanceStore;
    }

    public void reload(MegaphoneSettings settings, boolean crossServerAvailable) {
        this.settings = settings == null ? MegaphoneSettings.defaults() : settings;
        balanceStore.reload(this.settings.dataFile(), crossServerAvailable);
    }

    public boolean shouldCapture(ChatChannel channel) {
        return settings.enabled()
            && settings.captureWorldChannel()
            && channel != null
            && "world".equalsIgnoreCase(channel.id());
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            if (sender instanceof Player player) {
                sendBalanceAsync(sender, player, "commands.ymchat.megaphone.balance", null, null);
            }
            return true;
        }

        String action = normalize(args[0]);
        if (matches(action, "help", "\u5e2e\u52a9")) {
            sendHelp(sender);
            return true;
        }
        if (matches(action, "balance", "\u6570\u91cf", "\u4f59\u989d", "\u67e5\u8be2")) {
            return handleBalance(sender, args);
        }
        if (matches(action, "give", "\u53d1\u653e", "\u589e\u52a0")) {
            return handleAdjust(sender, args, true);
        }
        if (matches(action, "take", "\u6263\u9664", "\u79fb\u9664")) {
            return handleAdjust(sender, args, false);
        }
        if (matches(action, "gui", "menu", "\u83dc\u5355", "\u754c\u9762")) {
            send(sender, "commands.ymchat.megaphone.gui-resource");
            return true;
        }

        MegaphoneMode mode = MegaphoneMode.parse(args[0]);
        if (mode == null) {
            send(sender, "commands.ymchat.megaphone.invalid-mode", "mode", args[0]);
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "commands.ymchat.megaphone.only-player");
            return true;
        }
        if (args.length < 2) {
            send(sender, "commands.ymchat.megaphone.missing-message");
            return true;
        }
        announce(player, mode, joinTail(args, 1));
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> legacy = List.of("chat", "title", "bossbar", "balance", "give", "take", "gui");
            return filter(legacy, args[0]);
        }

        String action = normalize(args[0]);
        if (args.length == 2 && (matches(action, "give", "\u53d1\u653e", "\u589e\u52a0")
            || matches(action, "take", "\u6263\u9664", "\u79fb\u9664")
            || matches(action, "balance", "\u6570\u91cf", "\u4f59\u989d", "\u67e5\u8be2"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && (matches(action, "give", "\u53d1\u653e", "\u589e\u52a0")
            || matches(action, "take", "\u6263\u9664", "\u79fb\u9664"))) {
            return filter(List.of("1", "3", "5", "10", "32"), args[2]);
        }
        return List.of();
    }

    public void announce(Player sender, MegaphoneMode mode, String message) {
        if (!settings.enabled()) {
            send(sender, "commands.ymchat.megaphone.disabled");
            return;
        }
        if (!hasPermission(sender, settings.usePermission())) {
            send(sender, "commands.ymchat.megaphone.no-permission");
            return;
        }

        MegaphoneSettings.ModeSettings modeSettings = settings.mode(mode);
        if (modeSettings == null || !modeSettings.enabled()) {
            send(sender, "commands.ymchat.megaphone.mode-disabled", "mode", modeDisplay(modeSettings, mode));
            return;
        }
        if (!hasPermission(sender, modeSettings.permission())) {
            send(sender, "commands.ymchat.megaphone.no-mode-permission", "mode", modeDisplay(modeSettings, mode));
            return;
        }
        if (message == null || message.isBlank()) {
            send(sender, "commands.ymchat.megaphone.missing-message");
            return;
        }
        balanceStore.consume(sender, modeSettings.cost()).whenComplete((result, throwable) -> {
            if (throwable != null) {
                handleStorageFailure(sender, "consume megaphone balance", throwable);
                return;
            }
            runSenderTask(sender, () -> {
                if (!sender.isOnline()) {
                    return;
                }
                if (!result.allowed()) {
                    send(
                        sender,
                        "commands.ymchat.megaphone.not-enough",
                        "cost", String.valueOf(modeSettings.cost()),
                        "balance", String.valueOf(result.balance())
                    );
                    return;
                }

                String sanitized = message.trim();
                switch (mode) {
                    case CHAT -> broadcastChat(sender, sanitized, modeSettings);
                    case TITLE -> broadcastTitle(sender, sanitized, modeSettings);
                    case BOSSBAR -> broadcastBossBar(sender, sanitized, modeSettings);
                }
                send(
                    sender,
                    "commands.ymchat.megaphone.sent",
                    "mode", modeDisplay(modeSettings, mode),
                    "cost", String.valueOf(modeSettings.cost()),
                    "balance", String.valueOf(result.balance())
                );
            });
        });
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        if (args.length >= 2 && sender.hasPermission(settings.adminPermission())) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            sendBalanceAsync(sender, target, "commands.ymchat.megaphone.target-balance", "player", displayName(target, args[1]));
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "commands.ymchat.megaphone.only-player");
            return true;
        }
        sendBalanceAsync(sender, player, "commands.ymchat.megaphone.balance", null, null);
        return true;
    }

    private boolean handleAdjust(CommandSender sender, String[] args, boolean give) {
        if (!sender.hasPermission(settings.adminPermission())) {
            send(sender, "commands.ymchat.megaphone.admin-no-permission");
            return true;
        }
        if (args.length < 3) {
            send(sender, give ? "commands.ymchat.megaphone.give-usage" : "commands.ymchat.megaphone.take-usage");
            return true;
        }
        int amount = parseAmount(args[2]);
        if (amount <= 0) {
            send(sender, "commands.ymchat.megaphone.invalid-amount", "amount", args[2]);
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        (give ? balanceStore.give(target, amount) : balanceStore.take(target, amount)).whenComplete((balance, throwable) -> {
            if (throwable != null) {
                handleStorageFailure(sender, give ? "give megaphone balance" : "take megaphone balance", throwable);
                return;
            }
            runSenderTask(sender, () -> send(
                sender,
                give ? "commands.ymchat.megaphone.give-success" : "commands.ymchat.megaphone.take-success",
                "player", displayName(target, args[1]),
                "amount", String.valueOf(amount),
                "balance", String.valueOf(balance)
            ));
            Player onlineTarget = target.getUniqueId() == null ? null : Bukkit.getPlayer(target.getUniqueId());
            if (onlineTarget != null && onlineTarget.isOnline()) {
                plugin.getPlatformBridge().runForPlayer(onlineTarget, () -> {
                    if (onlineTarget.isOnline()) {
                        send(
                            onlineTarget,
                            give ? "commands.ymchat.megaphone.give-notify" : "commands.ymchat.megaphone.take-notify",
                            "amount", String.valueOf(amount),
                            "balance", String.valueOf(balance)
                        );
                    }
                });
            }
        });
        return true;
    }

    private void broadcastChat(Player sender, String message, MegaphoneSettings.ModeSettings modeSettings) {
        Component component = RichText.deserialize(placeholders(sender, message, modeSettings.chatFormat()));
        for (Player recipient : onlinePlayers()) {
            plugin.getPlatformBridge().runForPlayer(recipient, () -> {
                if (recipient.isOnline()) {
                    plugin.getPlatformBridge().sendMessage(recipient, component);
                }
            });
        }
        plugin.getPlatformBridge().sendConsoleMessage(component);
    }

    @SuppressWarnings("deprecation")
    private void broadcastTitle(Player sender, String message, MegaphoneSettings.ModeSettings modeSettings) {
        String rawTitle = placeholders(sender, message, modeSettings.title());
        String rawSubtitle = placeholders(sender, message, modeSettings.subtitle());
        String title = RichText.toLegacySectionString(rawTitle);
        String subtitle = RichText.toLegacySectionString(rawSubtitle);
        for (Player recipient : onlinePlayers()) {
            plugin.getPlatformBridge().runForPlayer(recipient, () -> {
                if (recipient.isOnline()) {
                    recipient.sendTitle(title, subtitle, modeSettings.fadeInTicks(), modeSettings.stayTicks(), modeSettings.fadeOutTicks());
                }
            });
        }
        plugin.getPlatformBridge().sendConsoleMessage(RichText.deserialize(rawTitle + " " + rawSubtitle));
    }

    private void broadcastBossBar(Player sender, String message, MegaphoneSettings.ModeSettings modeSettings) {
        String text = RichText.toLegacySectionString(placeholders(sender, message, modeSettings.bossBarText()));
        BarColor color = parseEnum(BarColor.class, modeSettings.bossBarColor(), BarColor.YELLOW);
        BarStyle style = parseEnum(BarStyle.class, modeSettings.bossBarStyle(), BarStyle.SOLID);
        for (Player recipient : onlinePlayers()) {
            plugin.getPlatformBridge().runForPlayer(recipient, () -> {
                if (!recipient.isOnline()) {
                    return;
                }
                BossBar bossBar = Bukkit.createBossBar(initialBossBarTitle(text, modeSettings), color, style);
                bossBar.setProgress(modeSettings.bossBarProgress());
                bossBar.addPlayer(recipient);
                scheduleBossBarAnimation(recipient, bossBar, text, modeSettings, 2, modeSettings.bossBarAnimationIntervalTicks());
                plugin.getPlatformBridge().runForPlayerLater(recipient, () -> bossBar.removeAll(), modeSettings.bossBarDurationTicks());
            });
        }
        plugin.getPlatformBridge().sendConsoleMessage(RichText.deserialize(text));
    }

    private String initialBossBarTitle(String text, MegaphoneSettings.ModeSettings modeSettings) {
        if (!modeSettings.bossBarAnimationEnabled()) {
            return text;
        }
        return BossBarTextAnimator.frame(text, modeSettings.bossBarAnimationStyle(), 1, modeSettings.bossBarAnimationFlowWidth());
    }

    private void scheduleBossBarAnimation(
        Player recipient,
        BossBar bossBar,
        String text,
        MegaphoneSettings.ModeSettings modeSettings,
        int step,
        int elapsedTicks
    ) {
        if (!modeSettings.bossBarAnimationEnabled() || elapsedTicks >= modeSettings.bossBarDurationTicks()) {
            return;
        }
        int interval = modeSettings.bossBarAnimationIntervalTicks();
        plugin.getPlatformBridge().runForPlayerLater(recipient, () -> {
            if (!recipient.isOnline()) {
                bossBar.removeAll();
                return;
            }
            bossBar.setTitle(BossBarTextAnimator.frame(text, modeSettings.bossBarAnimationStyle(), step, modeSettings.bossBarAnimationFlowWidth()));
            scheduleBossBarAnimation(recipient, bossBar, text, modeSettings, step + 1, elapsedTicks + interval);
        }, interval);
    }

    private List<Player> onlinePlayers() {
        return new ArrayList<>(Bukkit.getOnlinePlayers());
    }

    private String placeholders(Player sender, String message, String template) {
        String resolved = template == null || template.isBlank() ? "%message%" : template;
        String withMessageToken = resolved.replace("%message%", MESSAGE_TOKEN_PLACEHOLDER);
        String withPlaceholders = PlaceholderResolver.resolve(sender, withMessageToken);
        return ColorGradientUtil.applyNameColorTokens(withPlaceholders, resolveNameColor(sender), "&f")
            .replace(MESSAGE_TOKEN_PLACEHOLDER, message);
    }

    private PlayerColorService.ResolvedColor resolveNameColor(Player sender) {
        PlayerColorService colorService = plugin.getPlayerColorService();
        if (colorService == null || plugin.getChatConfig() == null) {
            return null;
        }
        return colorService.resolve(
            sender,
            ColorScope.NAME,
            plugin.getChatConfig().nameColorSettings(),
            "&f"
        );
    }

    private String modeDisplay(MegaphoneSettings.ModeSettings modeSettings, MegaphoneMode mode) {
        if (modeSettings != null && modeSettings.display() != null && !modeSettings.display().isBlank()) {
            return plugin.getLanguageService().resolveConfigured(modeSettings.display());
        }
        return switch (mode) {
            case CHAT -> plugin.getLanguageService().get("megaphone.modes.chat.display");
            case TITLE -> plugin.getLanguageService().get("megaphone.modes.title.display");
            case BOSSBAR -> plugin.getLanguageService().get("megaphone.modes.bossbar.display");
        };
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.getLanguageService().getList("commands.ymchat.megaphone.help")) {
            CommandMessagesLike.send(sender, line);
        }
    }

    private void send(CommandSender sender, String key, String... placeholders) {
        CommandMessagesLike.send(sender, plugin.getLanguageService().get(key, placeholders));
    }

    private void sendBalanceAsync(CommandSender sender, OfflinePlayer target, String key, String placeholderName, String placeholderValue) {
        balanceStore.queryBalance(target).whenComplete((balance, throwable) -> {
            if (throwable != null) {
                handleStorageFailure(sender, "query megaphone balance", throwable);
                return;
            }
            if (placeholderName == null || placeholderName.isBlank()) {
                runSenderTask(sender, () -> send(sender, key, "amount", String.valueOf(balance)));
                return;
            }
            runSenderTask(sender, () -> send(sender, key,
                placeholderName, placeholderValue == null ? "" : placeholderValue,
                "amount", String.valueOf(balance)
            ));
        });
    }

    private void handleStorageFailure(CommandSender sender, String action, Throwable throwable) {
        Throwable cause = unwrapCompletionException(throwable);
        plugin.getLogger().warning("Failed to " + action + ": " + cause.getMessage());
        runSenderTask(sender, () -> send(sender, "commands.ymchat.megaphone.unavailable"));
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private void runSenderTask(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            plugin.getPlatformBridge().runForPlayer(player, () -> {
                if (player.isOnline()) {
                    task.run();
                }
            });
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return permission == null || permission.isBlank() || sender.hasPermission(permission);
    }

    private int parseAmount(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String displayName(OfflinePlayer player, String fallback) {
        String name = player == null ? null : player.getName();
        return name == null || name.isBlank() ? fallback : name;
    }

    private String joinTail(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
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

    private <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static final class CommandMessagesLike {
        private CommandMessagesLike() {
        }

        private static void send(CommandSender sender, String message) {
            sender.sendMessage(RichText.toLegacySectionString(message));
        }
    }
}
