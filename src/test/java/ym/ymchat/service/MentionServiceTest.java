package ym.ymchat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.chat.MentionSettings;
import ym.ymchat.service.chat.MentionService;
import ym.ymchat.service.color.PlayerColorService;
import ym.ymchat.service.color.PublicChatColorService;
import ym.ymchat.service.text.RichText;

class MentionServiceTest {

    private final MentionService mentionService = new MentionService();
    private final PublicChatColorService colorService = new PublicChatColorService();

    @Test
    void extractsMentionsFromVisiblePlainText() {
        MentionSettings settings = new MentionSettings(true, "@", "&b", "ENTITY_EXPERIENCE_ORB_PICKUP", true, true, "ymchat.mention.everyone", "all");

        MentionService.MentionResult result = mentionService.extractMentions(
            "&e@Alice hello @all",
            settings,
            name -> name.equalsIgnoreCase("Alice"),
            true
        );

        assertEquals(List.of("Alice"), result.mentionedNames());
        assertTrue(result.everyoneMentioned());
        assertEquals(2, result.highlightRanges().size());
    }

    @Test
    void appliesHighlightColorOnTopOfPreparedMessage() {
        MentionSettings settings = new MentionSettings(true, "@", "&b", "ENTITY_EXPERIENCE_ORB_PICKUP", true, true, "ymchat.mention.everyone", "all");
        PublicChatColorService.PreparedPublicChatMessage prepared = colorService.prepare(
            "&e@Alice hello",
            "&f",
            new PlayerColorService.ResolvedColor("&f", PlayerColorService.ColorSource.RULE_DEFAULT, null, null),
            new PublicChatColorService.PermissionAccess(true, false, false)
        );

        MentionService.MentionResult result = mentionService.extractMentions(
            prepared.visiblePlainText(),
            settings,
            name -> name.equalsIgnoreCase("Alice"),
            false
        );

        String serialized = RichText.serializeToSection(mentionService.applyHighlights(prepared, settings, result));
        assertEquals("\u00a7b@Alice\u00a7e hello", serialized);
    }
}
