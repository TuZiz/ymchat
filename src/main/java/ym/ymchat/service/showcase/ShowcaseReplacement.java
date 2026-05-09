package ym.ymchat.service.showcase;

import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;

public record ShowcaseReplacement(
    ShowcaseType type,
    Pattern tokenPattern,
    Component replacement,
    int maxReplacements
) {

    public ShowcaseReplacement {
        maxReplacements = Math.max(1, maxReplacements);
    }
}
