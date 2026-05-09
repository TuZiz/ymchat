package ym.ymchat.service.chat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.command.CommandMessages;
import ym.ymchat.config.chat.PrivateMessageRule;
import ym.ymchat.config.chat.PrivateMessageSettings;

import ym.ymchat.service.text.PlaceholderResolver;
import ym.ymchat.service.text.RichText;
public final class PrivateMessageService {

    private final YmChatPlugin plugin;
    private final Map<UUID, UUID> lastContact = new ConcurrentHashMap<>();
    private final Set<UUID> socialSpy = ConcurrentHashMap.newKeySet();

    public PrivateMessageService(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendPrivateMessage(Player sender, Player target, String rawMessage) {
        PrivateMessageSettings settings = plugin.getChatConfig().privateMessageSettings();
        if (!settings.enabled()) {
            CommandMessages.send(sender, settings.disabledMessage());
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            CommandMessages.send(sender, settings.selfMessage());
            return;
        }

        TextFilterService.FilterResult filtered = plugin.getTextFilterService().apply(
            sender,
            rawMessage,
            "private",
            "private",
            plugin.getChatConfig().filterSettings()
        );
        if (!filtered.allowed()) {
            CommandMessages.send(sender, filtered.message());
            plugin.getDebugService().traceFilterBlock(sender, "private", "private", filtered.describeHits());
            return;
        }

        Component messageComponent = RichText.deserialize("&f" + filtered.message());
        PrivateMessageRule rule = settings.firstMatching(sender, target);
        Component senderMessage = renderTemplate(sender, target, rule.senderFormat(), messageComponent);
        Component receiverMessage = renderTemplate(sender, target, rule.receiverFormat(), messageComponent);
        Component spyMessage = renderTemplate(sender, target, rule.spyFormat(), messageComponent);

        plugin.getPlatformBridge().runForPlayer(sender, () -> plugin.getPlatformBridge().sendMessage(sender, senderMessage));
        plugin.getPlatformBridge().runForPlayer(target, () -> plugin.getPlatformBridge().sendMessage(target, receiverMessage));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(sender.getUniqueId()) || online.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            if (isSocialSpyEnabled(online) && online.hasPermission("ymchat.socialspy")) {
                plugin.getPlatformBridge().runForPlayer(online, () -> plugin.getPlatformBridge().sendMessage(online, spyMessage));
            }
        }

        lastContact.put(sender.getUniqueId(), target.getUniqueId());
        lastContact.put(target.getUniqueId(), sender.getUniqueId());
        plugin.getDebugService().tracePrivateMessage(sender, target, rule, filtered.describeHits());
    }

    public void reply(Player sender, String rawMessage) {
        PrivateMessageSettings settings = plugin.getChatConfig().privateMessageSettings();
        UUID targetId = lastContact.get(sender.getUniqueId());
        if (targetId == null) {
            CommandMessages.send(sender, settings.noReplyTargetMessage());
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            CommandMessages.send(sender, offlineMessage(describeReplyTarget(sender)));
            return;
        }
        sendPrivateMessage(sender, target, rawMessage);
    }

    public boolean toggleSocialSpy(Player player) {
        if (socialSpy.contains(player.getUniqueId())) {
            socialSpy.remove(player.getUniqueId());
            return false;
        }
        socialSpy.add(player.getUniqueId());
        return true;
    }

    public boolean isSocialSpyEnabled(Player player) {
        return socialSpy.contains(player.getUniqueId());
    }

    public String describeReplyTarget(Player player) {
        UUID targetId = lastContact.get(player.getUniqueId());
        if (targetId == null) {
            return plugin.getLanguageService().get("common.none");
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            return target.getName();
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetId);
        return offlinePlayer.getName() == null ? plugin.getLanguageService().get("common.offline") : offlinePlayer.getName();
    }

    public String offlineMessage(String targetName) {
        return plugin.getChatConfig().privateMessageSettings().offlineMessage().replace("%target_name%", targetName);
    }

    private Component renderTemplate(Player sender, Player target, String template, Component messageComponent) {
        String resolved = PlaceholderResolver.resolve(sender, template).replace("%target_name%", target.getName());
        if (!resolved.contains("{message}")) {
            return RichText.deserialize(resolved);
        }
        TextComponent.Builder builder = Component.text();
        int cursor = 0;
        while (cursor < resolved.length()) {
            int token = resolved.indexOf("{message}", cursor);
            if (token < 0) {
                builder.append(RichText.deserialize(resolved.substring(cursor)));
                break;
            }
            if (token > cursor) {
                builder.append(RichText.deserialize(resolved.substring(cursor, token)));
            }
            builder.append(messageComponent);
            cursor = token + "{message}".length();
        }
        return builder.build();
    }
}
