package ym.ymchat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.color.ColorPreset;
import ym.ymchat.service.color.PlayerColorPreference;
import ym.ymchat.service.color.PlayerColorService;
import ym.ymchat.service.color.PublicChatColorService;
import ym.ymchat.service.text.RichText;

class PublicChatColorServiceTest {

    private final PublicChatColorService service = new PublicChatColorService();

    @Test
    void appliesFixedBaseColorWhenNoInlineFormattingIsUsed() {
        PublicChatColorService.PreparedPublicChatMessage prepared = service.plain("hello", "&a");

        assertEquals("hello", prepared.visiblePlainText());
        assertEquals("\u00a7ahello", RichText.serializeToSection(prepared.toComponent()));
    }

    @Test
    void appliesAuthorizedLegacyColorCodes() {
        PublicChatColorService.PreparedPublicChatMessage prepared = service.prepare(
            "&ehello",
            "&f",
            new PlayerColorService.ResolvedColor("&a", PlayerColorService.ColorSource.RULE_DEFAULT, null, null),
            new PublicChatColorService.PermissionAccess(true, false, false)
        );

        assertEquals("hello", prepared.visiblePlainText());
        assertTrue(prepared.usedInlineFormatting());
        assertEquals("\u00a7ehello", RichText.serializeToSection(prepared.toComponent()));
    }

    @Test
    void keepsUnauthorizedCodesAsVisibleText() {
        PublicChatColorService.PreparedPublicChatMessage prepared = service.prepare(
            "&ehello",
            "&f",
            new PlayerColorService.ResolvedColor("&a", PlayerColorService.ColorSource.RULE_DEFAULT, null, null),
            new PublicChatColorService.PermissionAccess(false, false, false)
        );

        assertEquals("&ehello", prepared.visiblePlainText());
        assertFalse(prepared.usedInlineFormatting());
        assertTrue(prepared.hadUnauthorizedFormatting());
        assertEquals("\u00a7a&ehello", RichText.serializeToSection(prepared.toComponent()));
    }

    @Test
    void appliesRgbAndResetBackToBaseColor() {
        PublicChatColorService.PreparedPublicChatMessage prepared = service.prepare(
            "&#55CCFFsky<reset>end",
            "&f",
            new PlayerColorService.ResolvedColor("&6", PlayerColorService.ColorSource.RULE_DEFAULT, null, null),
            new PublicChatColorService.PermissionAccess(false, false, true)
        );

        assertEquals("skyend", prepared.visiblePlainText());
        String json = GsonComponentSerializer.gson().serialize(prepared.toComponent()).toLowerCase(Locale.ROOT);
        String serialized = RichText.serializeToSection(prepared.toComponent()).toLowerCase(Locale.ROOT);
        assertTrue(json.contains("\"color\":\"#55ccff\""));
        assertTrue(serialized.endsWith("\u00a76end"));
    }

    @Test
    void appliesAuthorizedFormatCodesWithoutChangingBaseColor() {
        PublicChatColorService.PreparedPublicChatMessage prepared = service.prepare(
            "&lhello",
            "&f",
            new PlayerColorService.ResolvedColor("&e", PlayerColorService.ColorSource.RULE_DEFAULT, null, null),
            new PublicChatColorService.PermissionAccess(false, true, false)
        );

        assertEquals("hello", prepared.visiblePlainText());
        assertEquals("\u00a7e\u00a7lhello", RichText.serializeToSection(prepared.toComponent()));
    }

    @Test
    void appliesConfiguredGradientAcrossMessageText() {
        PublicChatColorService.PreparedPublicChatMessage prepared = service.prepare(
            "ab",
            "&f",
            new PlayerColorService.ResolvedColor(
                "&#AA1122",
                PlayerColorService.ColorSource.MANUAL_RGB,
                PlayerColorPreference.rgb("rainbow"),
                new ColorPreset(
                    "rainbow",
                    "Rainbow",
                    "ymchat.color.rgb.rainbow",
                    "#FFFFFF",
                    java.util.List.of("#AA1122", "#2244CC")
                )
            ),
            new PublicChatColorService.PermissionAccess(false, false, false)
        );

        String json = GsonComponentSerializer.gson().serialize(prepared.toComponent()).toLowerCase(Locale.ROOT);

        assertEquals("ab", prepared.visiblePlainText());
        assertTrue(prepared.spans().getFirst().hasGradient());
        assertTrue(json.contains("\"color\":\"#aa1122\""));
        assertTrue(json.contains("\"color\":\"#2244cc\""));
    }
}
