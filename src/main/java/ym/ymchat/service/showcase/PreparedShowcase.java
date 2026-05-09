package ym.ymchat.service.showcase;

import java.util.List;
import net.kyori.adventure.text.Component;

public record PreparedShowcase(
    boolean requested,
    String blockedMessage,
    List<? extends ShowcaseReplacement> replacements
) {

    public PreparedShowcase {
        replacements = replacements == null ? List.of() : List.copyOf(replacements);
    }

    public static PreparedShowcase none() {
        return new PreparedShowcase(false, null, List.of());
    }

    public static PreparedShowcase blocked(String message) {
        return new PreparedShowcase(true, message, List.of());
    }

    public static PreparedShowcase ready(List<? extends ShowcaseReplacement> replacements) {
        return new PreparedShowcase(true, null, replacements);
    }

    public Component replacement() {
        return replacements.isEmpty() ? null : replacements.getFirst().replacement();
    }

    public boolean ready() {
        return requested && blockedMessage == null && !replacements.isEmpty();
    }

    public boolean blocked() {
        return requested && blockedMessage != null;
    }
}
