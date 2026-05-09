package ym.ymchat.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.highlight.KeywordHighlightRule;
import ym.ymchat.config.chat.MentionSettings;
import ym.ymchat.config.highlight.PublicChatHighlightSettings;
import ym.ymchat.service.chat.MentionService;
import ym.ymchat.service.chat.PublicChatHighlightService;
import ym.ymchat.service.color.PlayerColorService;
import ym.ymchat.service.color.PublicChatColorService;
import ym.ymchat.service.showcase.ItemShowcaseService;
import ym.ymchat.service.showcase.PreparedShowcase;
import ym.ymchat.service.showcase.ShowcaseReplacement;
import ym.ymchat.service.showcase.ShowcaseType;
import ym.ymchat.service.text.RichText;

class PublicChatHighlightServiceTest {

    private final PublicChatHighlightService service = new PublicChatHighlightService();
    private final PublicChatColorService colorService = new PublicChatColorService();
    private final MentionService mentionService = new MentionService();

    @Test
    void appliesKeywordAndPatternHighlights() {
        PublicChatColorService.PreparedPublicChatMessage highlighted = service.apply(
            colorService.plain("sell 1000 x64 123 64 -90 8:30", "&f"),
            "sell 1000 x64 123 64 -90 8:30",
            "global",
            PublicChatHighlightSettings.defaults()
        );

        String json = GsonComponentSerializer.gson().serialize(highlighted.toComponent()).toLowerCase(Locale.ROOT);

        assertTrue(json.contains("\"color\":\"#ffd166\""));
        assertTrue(json.contains("\"color\":\"#f7b32b\""));
        assertTrue(json.contains("\"color\":\"#4ecdc4\""));
        assertTrue(json.contains("\"color\":\"#5da9e9\""));
        assertTrue(json.contains("\"color\":\"#c77dff\""));
    }

    @Test
    void higherPriorityRuleWinsForOverlap() {
        PublicChatHighlightSettings settings = new PublicChatHighlightSettings(
            true,
            List.of("*"),
            List.of(
                new KeywordHighlightRule("high", true, 100, List.of("*"), "regex", "sell item", false, false, "&#FF0000", List.of()),
                new KeywordHighlightRule("low", true, 10, List.of("*"), "literal", "sell", false, false, "&#0000FF", List.of())
            ),
            List.of()
        );

        PublicChatColorService.PreparedPublicChatMessage highlighted = service.apply(
            colorService.plain("sell item", "&f"),
            "sell item",
            "global",
            settings
        );

        String json = GsonComponentSerializer.gson().serialize(highlighted.toComponent()).toLowerCase(Locale.ROOT);

        assertTrue(json.contains("\"color\":\"#ff0000\""));
        assertFalse(json.contains("\"color\":\"#0000ff\""));
    }

    @Test
    void usesFilteredPlainTextWithoutReplayingOriginalInlineFormatting() {
        PublicChatColorService.PreparedPublicChatMessage original = colorService.prepare(
            "&lPrice 1000",
            "&f",
            new PlayerColorService.ResolvedColor("&a", PlayerColorService.ColorSource.RULE_DEFAULT, null, null),
            new PublicChatColorService.PermissionAccess(false, true, false)
        );
        PublicChatColorService.PreparedPublicChatMessage filtered = colorService.plain("Price 1000", original.baseColorValue());

        PublicChatColorService.PreparedPublicChatMessage highlighted = service.apply(
            filtered,
            "Price 1000",
            "global",
            PublicChatHighlightSettings.defaults()
        );

        String serialized = RichText.serializeToSection(highlighted.toComponent());

        assertTrue(serialized.startsWith("\u00a7aPrice"));
        assertFalse(serialized.startsWith("\u00a7a\u00a7l"));
    }

    @Test
    void mentionHighlightOverridesRegularHighlight() {
        PublicChatHighlightSettings settings = new PublicChatHighlightSettings(
            true,
            List.of("*"),
            List.of(new KeywordHighlightRule("mention", true, 100, List.of("*"), "literal", "@Alice", false, false, "&#FF0000", List.of("bold"))),
            List.of()
        );

        PublicChatColorService.PreparedPublicChatMessage highlighted = service.apply(
            colorService.plain("@Alice hi", "&f"),
            "@Alice hi",
            "global",
            settings
        );
        MentionService.MentionResult mentions = mentionService.extractMentions(
            "@Alice hi",
            MentionSettings.defaults(),
            name -> name.equalsIgnoreCase("Alice"),
            false
        );

        Component component = mentionService.applyHighlights(highlighted, MentionSettings.defaults(), mentions);
        String serialized = RichText.serializeToSection(component);

        assertTrue(serialized.startsWith("\u00a7e\u00a7l@Alice"));
    }

    @Test
    void respectsChannelScopes() {
        PublicChatHighlightSettings settings = new PublicChatHighlightSettings(
            true,
            List.of("trade"),
            List.of(new KeywordHighlightRule("trade", true, 100, List.of(), "literal", "sell", false, false, "&#FFD166", List.of())),
            List.of()
        );

        PublicChatColorService.PreparedPublicChatMessage worldMessage = service.apply(
            colorService.plain("sell 1000", "&f"),
            "sell 1000",
            "world",
            settings
        );

        String json = GsonComponentSerializer.gson().serialize(worldMessage.toComponent()).toLowerCase(Locale.ROOT);

        assertFalse(json.contains("#ffd166"));
    }

    @Test
    void keepsItemShowcaseReplacementAvailableAfterHighlighting() {
        PublicChatColorService.PreparedPublicChatMessage highlighted = service.apply(
            colorService.plain("show [i] 1000", "&f"),
            "show [i] 1000",
            "global",
            PublicChatHighlightSettings.defaults()
        );

        PreparedShowcase showcase = new PreparedShowcase(
            true,
            null,
            java.util.List.of(new ShowcaseReplacement(
                ShowcaseType.ITEM,
                java.util.regex.Pattern.compile(java.util.regex.Pattern.quote("[i]")),
                Component.text("ITEM"),
                1
            ))
        );
        Component applied = new ItemShowcaseService().apply(highlighted.toComponent(), showcase);
        String serialized = RichText.serializeToSection(applied);

        assertTrue(serialized.contains("ITEM"));
        assertTrue(serialized.contains("1000"));
    }
}
