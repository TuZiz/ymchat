package ym.ymchat.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class YmChatCommandTest {

    private final YmChatCommand command = new YmChatCommand(null);

    @Test
    void rootTabCompletionShowsEnglishSubcommands() {
        List<String> completions = command.onTabComplete(null, null, "ymchat", new String[]{""});

        assertTrue(completions.contains("reload"));
        assertTrue(completions.contains("logs"));
        assertTrue(completions.contains("megaphone"));
        assertTrue(completions.contains("namecolor"));
        assertFalse(completions.contains("\u91cd\u8f7d"));
    }

    @Test
    void legacyRootTabCompletionStillWorksWhenPlayerTypesEnglish() {
        assertEquals(List.of("reload"), command.onTabComplete(null, null, "ymchat", new String[]{"re"}));
    }

    @Test
    void debugTabCompletionShowsEnglishActions() {
        assertEquals(
            List.of("on", "off"),
            command.onTabComplete(null, null, "ymchat", new String[]{"\u8c03\u8bd5", ""})
        );
        assertEquals(List.of("off"), command.onTabComplete(null, null, "ymchat", new String[]{"debug", "of"}));
    }

    @Test
    void megaphoneLegacyRootTabCompletionStillWorks() {
        assertEquals(List.of("megaphone"), command.onTabComplete(null, null, "ymchat", new String[]{"me"}));
    }
}
