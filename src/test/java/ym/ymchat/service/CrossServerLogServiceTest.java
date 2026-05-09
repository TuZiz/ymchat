package ym.ymchat.service.crossserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.crossserver.CrossServerLogSettings;

class CrossServerLogServiceTest {

    private final CrossServerLogService service = new CrossServerLogService(null);

    @Test
    void parsesCombinedFiltersAndKeywordWithSpaces() {
        CrossServerLogService.ParseResult result = service.parseArguments(
            List.of("player:Tu_zi", "channel:trade", "keyword:diamond sword", "since:6h", "page:2", "limit:20"),
            CrossServerLogSettings.defaults()
        );

        assertTrue(result.valid());
        assertFalse(result.helpRequested());
        assertEquals("Tu_zi", result.query().player());
        assertEquals("trade", result.query().channel());
        assertEquals("diamond sword", result.query().keyword());
        assertEquals("6h", result.query().sinceToken());
        assertEquals(Duration.ofHours(6), result.query().since());
        assertEquals(2, result.query().page());
        assertEquals(20, result.query().limit());
    }

    @Test
    void buildsFollowupCommand() {
        CrossServerLogService.ParseResult result = service.parseArguments(
            List.of("player:Alex", "channel:global", "keyword:hello", "since:6h", "page:2", "limit:20"),
            CrossServerLogSettings.defaults()
        );

        assertTrue(result.valid());
        assertEquals("/ymchat logs player:Alex channel:global keyword:hello since:6h page:3 limit:20", result.query().toCommand(3));
        assertEquals("player=Alex, channel=global, keyword=hello, since=6h", result.query().describeFilters(null));
    }

    @Test
    void rejectsInvalidSinceArgument() {
        CrossServerLogService.ParseResult result = service.parseArguments(List.of("since:tomorrow"), CrossServerLogSettings.defaults());

        assertFalse(result.valid());
        assertEquals("since", result.errorMessage());
    }

    @Test
    void buildsInteractiveLogLineWithCopyAndHover() {
        CrossServerChatService.LogEntry entry = new CrossServerChatService.LogEntry(
            42L,
            "survival-1",
            "Survival",
            "global",
            "Tu_zi",
            "hello world",
            Instant.parse("2026-04-16T10:15:30Z")
        );

        var component = service.buildLine(entry, CrossServerLogSettings.defaults());

        assertNotNull(component.hoverEvent());
        assertNotNull(component.clickEvent());
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, component.clickEvent().action());
        assertTrue(component.clickEvent().value().contains("Survival/global"));
        assertTrue(component.clickEvent().value().contains("hello world"));
    }
}
