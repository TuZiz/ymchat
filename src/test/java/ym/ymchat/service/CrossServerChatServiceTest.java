package ym.ymchat.service.crossserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import java.time.Instant;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.crossserver.DatabaseSettings;
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
}
