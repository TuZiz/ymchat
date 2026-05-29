package ym.ymchat.config;

import java.util.Comparator;
import java.util.List;
import org.bukkit.entity.Player;
import ym.ymchat.config.chat.AntiSpamSettings;
import ym.ymchat.config.chat.ChannelSwitchSettings;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.chat.FormatRule;
import ym.ymchat.config.chat.MentionSettings;
import ym.ymchat.config.chat.PrivateMessageSettings;
import ym.ymchat.config.chat.TargetMode;
import ym.ymchat.config.color.ColorChatSettings;
import ym.ymchat.config.crossserver.CrossServerSettings;
import ym.ymchat.config.filter.FilterSettings;
import ym.ymchat.config.highlight.PublicChatHighlightSettings;
import ym.ymchat.config.megaphone.MegaphoneSettings;
import ym.ymchat.config.showcase.ItemShowcaseSettings;

public record ChatPluginConfig(
    TargetMode targetMode,
    boolean autoJoin,
    boolean forceLegacy,
    boolean debug,
    boolean showChannelDisplay,
    String defaultChannelId,
    ChannelSwitchSettings channelSwitchSettings,
    List<ChatChannel> channels,
    CrossServerSettings crossServerSettings,
    AntiSpamSettings antiSpamSettings,
    MentionSettings mentionSettings,
    ColorChatSettings colorChatSettings,
    ym.ymchat.config.color.FixedColorSettings nameColorSettings,
    MegaphoneSettings megaphoneSettings,
    PublicChatHighlightSettings publicChatHighlightSettings,
    ItemShowcaseSettings itemShowcaseSettings,
    PrivateMessageSettings privateMessageSettings,
    FilterSettings filterSettings,
    List<FormatRule> formats
) {

    public ChatPluginConfig {
        formats = formats.stream()
            .sorted(Comparator.comparingInt(FormatRule::priority).reversed())
            .toList();
    }

    public FormatRule firstMatching(Player player, String channelId, String preferredFormatId) {
        if (preferredFormatId != null && !preferredFormatId.isBlank()) {
            for (FormatRule rule : formats) {
                if (preferredFormatId.equalsIgnoreCase(rule.id()) && rule.matches(player, channelId)) {
                    return rule;
                }
            }
            for (FormatRule rule : formats) {
                if (preferredFormatId.equalsIgnoreCase(rule.id()) && rule.matchesCondition(player)) {
                    return rule;
                }
            }
        }
        for (FormatRule rule : formats) {
            if (rule.matches(player, channelId)) {
                return rule;
            }
        }
        return formats.isEmpty() ? FormatRule.fallback() : formats.getFirst();
    }

    public FormatRule firstMatching(Player player) {
        return firstMatching(player, "", "");
    }

    public ChatChannel defaultChannel() {
        ChatChannel configured = findChannel(defaultChannelId);
        return configured != null ? configured : channels.isEmpty() ? ChatChannel.global() : channels.getFirst();
    }

    public ChatChannel findChannel(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        for (ChatChannel channel : channels) {
            if (channel.matches(input)) {
                return channel;
            }
        }
        return null;
    }
}
