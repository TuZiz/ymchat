package ym.ymchat.service.debug;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.chat.FormatRule;
import ym.ymchat.config.chat.PrivateMessageRule;

import ym.ymchat.service.chat.MentionService;
import ym.ymchat.service.text.RichText;
public final class DebugService {

    private final YmChatPlugin plugin;
    private final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();

    public DebugService(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isDebugEnabled(Player player) {
        return plugin.getChatConfig().debug() || debugPlayers.contains(player.getUniqueId());
    }

    public void setDebug(Player player, boolean enabled) {
        if (enabled) {
            debugPlayers.add(player.getUniqueId());
        } else {
            debugPlayers.remove(player.getUniqueId());
        }
    }

    public void traceChat(
        Player sender,
        ChatChannel channel,
        FormatRule rule,
        List<Player> recipients,
        MentionService.MentionResult mentionResult,
        String antiSpamReason,
        String filterHits
    ) {
        if (!isDebugEnabled(sender)) {
            return;
        }
        sender.sendMessage(RichText.toLegacySectionString(plugin.getLanguageService().get(
            "debug.chat",
            "channel", channel.id(),
            "format", safe(rule.id()),
            "priority", String.valueOf(rule.priority()),
            "recipients", String.valueOf(recipients.size()),
            "mentions", localizedValue(mentionResult.describe()),
            "filter", localizedValue(filterHits),
            "mode", plugin.getLanguageService().get(plugin.getChatConfig().forceLegacy() ? "debug.values.mode.legacy" : "debug.values.mode.auto"),
            "spam", localizedValue(antiSpamReason)
        )));
    }

    public void tracePrivateMessage(Player sender, Player target, PrivateMessageRule rule, String filterHits) {
        if (!isDebugEnabled(sender)) {
            return;
        }
        sender.sendMessage(RichText.toLegacySectionString(plugin.getLanguageService().get(
            "debug.private",
            "target", target.getName(),
            "rule", safe(rule.id()),
            "priority", String.valueOf(rule.priority()),
            "filter", localizedValue(filterHits)
        )));
    }

    public void traceSpamBlock(Player player, String reason) {
        if (isDebugEnabled(player)) {
            player.sendMessage(RichText.toLegacySectionString(plugin.getLanguageService().get(
                "debug.spam-block",
                "reason", localizedValue(reason)
            )));
        }
    }

    public void traceFilterBlock(Player player, String scope, String channelId, String hits) {
        if (isDebugEnabled(player)) {
            player.sendMessage(RichText.toLegacySectionString(plugin.getLanguageService().get(
                "debug.filter-block",
                "scope", localizedValue(scope),
                "channel", channelId,
                "hits", localizedValue(hits)
            )));
        }
    }

    public void traceCrossServerPublish(Player player, ChatChannel channel, String serverName) {
        if (isDebugEnabled(player)) {
            player.sendMessage(RichText.toLegacySectionString(plugin.getLanguageService().get(
                "debug.cross-server-publish",
                "channel", channel.id(),
                "origin", serverName
            )));
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? plugin.getLanguageService().get("debug.values.default") : value;
    }

    private String localizedValue(String value) {
        if (value == null || value.isBlank()) {
            return plugin.getLanguageService().get("common.none");
        }
        return switch (value) {
            case "none" -> plugin.getLanguageService().get("common.none");
            case "@all" -> plugin.getLanguageService().get("debug.values.mentions.everyone");
            case "allowed" -> plugin.getLanguageService().get("debug.values.spam.allowed");
            case "max-length" -> plugin.getLanguageService().get("debug.values.spam.max-length");
            case "caps-ratio" -> plugin.getLanguageService().get("debug.values.spam.caps-ratio");
            case "cooldown" -> plugin.getLanguageService().get("debug.values.spam.cooldown");
            case "duplicate" -> plugin.getLanguageService().get("debug.values.spam.duplicate");
            case "public" -> plugin.getLanguageService().get("debug.values.scope.public");
            case "private" -> plugin.getLanguageService().get("debug.values.scope.private");
            default -> value;
        };
    }
}
