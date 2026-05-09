package ym.ymchat.config.chat;

import org.bukkit.entity.Player;
import ym.ymchat.service.text.ConditionEvaluator;

public record PrivateMessageRule(
    String id,
    String condition,
    String targetCondition,
    int priority,
    String senderFormat,
    String receiverFormat,
    String spyFormat
) {

    public boolean matches(Player sender, Player target) {
        return ConditionEvaluator.evaluate(sender, condition)
            && ConditionEvaluator.evaluate(target, targetCondition);
    }

    public static PrivateMessageRule fallback(PrivateMessageSettings settings) {
        return new PrivateMessageRule(
            "default",
            "~",
            "~",
            0,
            settings.senderFormat(),
            settings.receiverFormat(),
            settings.spyFormat()
        );
    }
}
