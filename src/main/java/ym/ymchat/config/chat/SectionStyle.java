package ym.ymchat.config.chat;

import org.bukkit.entity.Player;
import ym.ymchat.service.text.ConditionEvaluator;

public record SectionStyle(
    String condition,
    String text,
    String hover,
    String command,
    String suggest,
    String url,
    String copy
) {

    public boolean matches(Player player) {
        return ConditionEvaluator.evaluate(player, condition);
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    public String firstActionValue() {
        if (command != null && !command.isBlank()) {
            return command;
        }
        if (suggest != null && !suggest.isBlank()) {
            return suggest;
        }
        if (url != null && !url.isBlank()) {
            return url;
        }
        if (copy != null && !copy.isBlank()) {
            return copy;
        }
        return null;
    }

    public static SectionStyle empty() {
        return new SectionStyle("~", "", null, null, null, null, null);
    }

    public static SectionStyle messageFallback() {
        return new SectionStyle("~", "{message}", null, null, null, null, null);
    }
}
