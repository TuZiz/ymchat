package ym.ymchat.config.chat;

import java.util.List;
import org.bukkit.entity.Player;
import ym.ymchat.service.text.ConditionEvaluator;

public record FormatRule(
    String id,
    String channel,
    String condition,
    int priority,
    MessageOptions messageOptions,
    List<SectionStyle> prefixVariants,
    List<SectionStyle> nameVariants,
    List<SectionStyle> messageVariants
) {

    public boolean matches(Player player, String channelId) {
        return matchesChannel(channelId) && ConditionEvaluator.evaluate(player, condition);
    }

    public boolean matchesCondition(Player player) {
        return ConditionEvaluator.evaluate(player, condition);
    }

    public boolean matchesChannel(String channelId) {
        return channel == null || channel.isBlank() || "~".equals(channel) || channel.equalsIgnoreCase(channelId);
    }

    public SectionStyle firstPrefixVariant(Player player) {
        return prefixVariants.stream().filter(style -> style.matches(player)).findFirst().orElse(SectionStyle.empty());
    }

    public SectionStyle firstNameVariant(Player player) {
        return nameVariants.stream().filter(style -> style.matches(player)).findFirst().orElse(SectionStyle.empty());
    }

    public SectionStyle firstMessageVariant(Player player) {
        return messageVariants.stream().filter(style -> style.matches(player)).findFirst().orElse(SectionStyle.messageFallback());
    }

    public static FormatRule fallback() {
        return new FormatRule(
            "default",
            "",
            "~",
            0,
            new MessageOptions("&f", null, null, null, null, null),
            List.of(SectionStyle.empty()),
            List.of(SectionStyle.empty()),
            List.of(SectionStyle.messageFallback())
        );
    }
}
