package ym.ymchat.service.crossserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.crossserver.DatabaseSettings;
import ym.ymchat.config.highlight.KeywordHighlightRule;
import ym.ymchat.config.highlight.PublicChatHighlightSettings;
import ym.ymchat.service.chat.PublicChatHighlightService;
import ym.ymchat.service.color.ColorScope;
import ym.ymchat.service.color.PlayerColorPreference;

class CrossServerChatServiceTest {

    @Test
    void hikariConfigUsesExplicitPostgresDriverClass() {
        DatabaseSettings database = new DatabaseSettings(
            "127.0.0.1", 5432, "ymchat", "public", "postgres", "secret", "ymchat_cross_messages", false
        );

        HikariConfig hikari = CrossServerChatService.createHikariConfig(database);

        assertEquals("org.postgresql.Driver", hikari.getDriverClassName());
        assertEquals(database.jdbcUrl(), hikari.getJdbcUrl());
        assertEquals(database.username(), hikari.getUsername());
    }

    @Test
    void logQueryPlanIncludesConfiguredFilters() {
        CrossServerChatService.LogQuery query = new CrossServerChatService.LogQuery(
            "Tu_zi", "trade", "diamond_100%", Instant.parse("2026-04-16T10:15:30Z"), 2, 20
        );

        CrossServerChatService.LogQueryPlan plan = CrossServerChatService.buildLogQueryPlan("public.ymchat_cross_messages", query, 2);

        assertTrue(plan.countSql().contains("created_at >= ?"));
        assertTrue(plan.countSql().contains("LOWER(sender_name) = LOWER(?)"));
        assertTrue(plan.countSql().contains("LOWER(channel_id) = LOWER(?)"));
        assertTrue(plan.countSql().contains("plain_text ILIKE ? ESCAPE"));
        assertEquals(List.of(
            java.sql.Timestamp.from(query.since()),
            "Tu_zi",
            "trade",
            "%diamond\\_100\\%%"
        ), plan.parameters());
        assertEquals(20, plan.limit());
        assertEquals(20, plan.offset());
    }

    @Test
    void extractsPlainTextFromSerializedComponentJson() {
        String json = GsonComponentSerializer.gson()
            .serialize(Component.text("Hello ").append(Component.text("world")));

        assertEquals("Hello world", CrossServerChatService.toPlainText(json));
    }

    @Test
    void playerColorTableNameFollowsCrossServerMessageTable() {
        assertEquals("public.ymchat_cross_messages_player_colors", CrossServerChatService.playerColorTableName("public.ymchat_cross_messages"));
    }

    @Test
    void playerColorSqlPlanUsesScopePrimaryKeyAndCompatMigration() {
        String table = CrossServerChatService.playerColorTableName("public.ymchat_cross_messages");
        List<String> schema = CrossServerChatService.playerColorSchemaStatements(table);

        assertTrue(schema.getFirst().contains("PRIMARY KEY (player_uuid, scope)"));
        assertTrue(schema.stream().anyMatch(sql -> sql.contains("ADD COLUMN IF NOT EXISTS scope")));
        assertTrue(schema.stream().anyMatch(sql -> sql.contains("DROP CONSTRAINT")));
        assertTrue(CrossServerChatService.playerColorSelectSql(table).contains("scope = ?"));
        assertTrue(CrossServerChatService.playerColorUpsertSql(table).contains("ON CONFLICT (player_uuid, scope)"));
        assertTrue(CrossServerChatService.playerColorDeleteSql(table).contains("scope = ?"));
        assertEquals("name", CrossServerChatService.normalizePlayerColorScope(ColorScope.NAME));
    }

    @Test
    void megaphoneBalanceTableNameFollowsCrossServerMessageTable() {
        assertEquals("public.ymchat_cross_messages_megaphone_balances", CrossServerChatService.megaphoneBalanceTableName("public.ymchat_cross_messages"));
    }

    @Test
    void normalizesCrossServerPlayerColorModes() {
        assertEquals("legacy", CrossServerChatService.normalizePlayerColorMode(PlayerColorPreference.legacy("d")));
        assertEquals("rgb", CrossServerChatService.normalizePlayerColorMode(PlayerColorPreference.rgb("pink")));
        assertEquals("off", CrossServerChatService.normalizePlayerColorMode(PlayerColorPreference.off()));
        assertThrows(IllegalArgumentException.class, () -> CrossServerChatService.normalizePlayerColorMode(PlayerColorPreference.preset("gold")));
    }

    @Test
    void mapsStoredCrossServerPlayerColorPreferences() {
        assertEquals(PlayerColorPreference.Mode.LEGACY, CrossServerChatService.mapPlayerColorPreference("legacy", "d").mode());
        assertEquals("pink", CrossServerChatService.mapPlayerColorPreference("rgb", "pink").value());
        assertEquals(PlayerColorPreference.Mode.OFF, CrossServerChatService.mapPlayerColorPreference("off", "").mode());
        assertNull(CrossServerChatService.mapPlayerColorPreference("", "d"));
    }

    @Test
    void remoteHighlightFallbackOnlyTouchesRenderedMessageSection() {
        Component rendered = Component.empty()
            .append(Component.text("[sell-server] ", NamedTextColor.GRAY))
            .append(Component.text("Alice", NamedTextColor.WHITE))
            .append(Component.text(": ").append(Component.text("sell diamond", NamedTextColor.WHITE)));

        Component highlighted = CrossServerMessageHighlighter.apply(
            rendered,
            "cross-server",
            new PublicChatHighlightService(),
            literalSellHighlightSettings()
        );
        String json = GsonComponentSerializer.gson().serialize(highlighted).toLowerCase(Locale.ROOT);

        assertEquals("[sell-server] Alice: sell diamond", CrossServerChatService.toPlainText(highlighted));
        assertEquals(1, countOccurrences(json, "\"color\":\"#ff0000\""));
    }

    @Test
    void remoteHighlightFallbackPreservesMessageInteractions() {
        Component rendered = Component.empty()
            .append(Component.text("Alice", NamedTextColor.WHITE))
            .append(Component.text(": ").append(Component.text("sell diamond", NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(Component.text("reply")))
                .clickEvent(ClickEvent.suggestCommand("/msg Alice "))));

        Component highlighted = CrossServerMessageHighlighter.apply(
            rendered,
            "cross-server",
            new PublicChatHighlightService(),
            literalSellHighlightSettings()
        );
        String json = GsonComponentSerializer.gson().serialize(highlighted).toLowerCase(Locale.ROOT);

        assertTrue(json.contains("\"color\":\"#ff0000\""));
        assertTrue(json.contains("show_text"));
        assertTrue(json.contains("suggest_command"));
    }

    private static PublicChatHighlightSettings literalSellHighlightSettings() {
        return new PublicChatHighlightSettings(
            true,
            List.of("*"),
            List.of(new KeywordHighlightRule(
                "sell",
                true,
                100,
                List.of("*"),
                "literal",
                "sell",
                List.of(),
                false,
                false,
                "&#FF0000",
                List.of(),
                List.of(),
                "",
                "",
                ""
            )),
            List.of()
        );
    }

    private static int countOccurrences(String input, String needle) {
        int count = 0;
        int index = 0;
        while ((index = input.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
