package ym.ymchat.config.chat;

import java.util.Comparator;
import java.util.List;
import org.bukkit.entity.Player;

public record PrivateMessageSettings(
    boolean enabled,
    String senderFormat,
    String receiverFormat,
    String spyFormat,
    String disabledMessage,
    String noReplyTargetMessage,
    String offlineMessage,
    String selfMessage,
    List<PrivateMessageRule> rules
) {

    public PrivateMessageSettings {
        rules = rules == null ? List.of() : rules.stream()
            .sorted(Comparator.comparingInt(PrivateMessageRule::priority).reversed())
            .toList();
    }

    public PrivateMessageRule firstMatching(Player sender, Player target) {
        for (PrivateMessageRule rule : rules) {
            if (rule.matches(sender, target)) {
                return rule;
            }
        }
        return PrivateMessageRule.fallback(this);
    }

    public static PrivateMessageSettings defaults() {
        return new PrivateMessageSettings(
            true,
            "&#FF99CC[me -> %target_name%] &#FFFFFF{message}",
            "&#FF99CC[%player_name% -> me] &#FFFFFF{message}",
            "&#777777[Spy] &#FF99CC%player_name% -> %target_name%: &#FFFFFF{message}",
            "&#777777[&#FF6B6B!&#777777] &#FF6B6BPrivate messages are disabled.",
            "&#777777[&#FF6B6B!&#777777] &#FF6B6BYou have no player to reply to.",
            "&#777777[&#FF6B6B!&#777777] &#FF6B6BPlayer %target_name% is not online.",
            "&#777777[&#FF6B6B!&#777777] &#FF6B6BYou cannot message yourself.",
            List.of()
        );
    }
}