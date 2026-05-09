package ym.ymchat.service.crossserver;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.crossserver.CrossServerSettings;
import ym.ymchat.config.crossserver.DatabaseSettings;
import ym.ymchat.config.chat.TargetMode;

import ym.ymchat.service.chat.MentionService;
import ym.ymchat.service.color.PlayerColorPreference;
import ym.ymchat.service.showcase.ShowcaseStoredSnapshot;
import ym.ymchat.service.text.RichText;
public final class CrossServerChatService implements AutoCloseable {

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int BACKFILL_BATCH_SIZE = 200;
    static final String POSTGRES_DRIVER = "org.postgresql.Driver";

    private final YmChatPlugin plugin;
    private HikariDataSource dataSource;
    private BukkitTask pollTask;
    private BukkitTask backfillTask;
    private CrossServerSettings settings = CrossServerSettings.defaults();
    private long lastSeenId;
    private Instant lastCleanupAt = Instant.EPOCH;
    private boolean warnedWorldChannel;

    public CrossServerChatService(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload(CrossServerSettings newSettings) {
        close();
        settings = newSettings == null ? CrossServerSettings.defaults() : newSettings;
        warnedWorldChannel = false;

        if (!settings.enabled()) {
            plugin.getLogger().info("YmChat cross-server sync is disabled.");
            return;
        }
        if (!settings.isConfigured()) {
            plugin.getLogger().warning("YmChat cross-server sync is enabled but not fully configured.");
            return;
        }

        try {
            dataSource = createDataSource(settings.database());
            initializeSchema();
            bootstrapCursor();
            startPolling();
            startPlainTextBackfill();
            plugin.getLogger().info("YmChat cross-server sync enabled for server " + settings.serverId()
                + " using " + settings.database().qualifiedTableName() + ".");
        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to initialize cross-server chat sync: " + exception.getMessage());
            close();
        }
    }

    public boolean isEnabled() {
        return dataSource != null;
    }

    public String describeStatus() {
        if (!settings.enabled()) {
            return "disabled";
        }
        if (!isEnabled()) {
            return "misconfigured";
        }
        return "postgresql:" + settings.serverId();
    }

    public void publish(Player sender, ChatChannel channel, Component component, MentionService.MentionResult mentionResult) {
        if (!isEnabled() || channel == null || !channel.crossServer()) {
            return;
        }
        if (channel.targetMode() != TargetMode.ALL) {
            if (!warnedWorldChannel) {
                warnedWorldChannel = true;
                plugin.getLogger().warning("Cross-server sync only supports ALL target channels. Channel "
                    + channel.id() + " will stay local because its target is " + channel.targetMode() + ".");
            }
            return;
        }

        String payload = GSON.serialize(component);
        String plainText = toPlainText(component);
        String mentioned = String.join(",", mentionResult.mentionedNames());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + settings.database().qualifiedTableName()
                         + " (server_id, server_name, channel_id, sender_uuid, sender_name, component_json, plain_text, mentioned_names, everyone_mentioned) "
                         + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, settings.serverId());
                statement.setString(2, settings.serverName());
                statement.setString(3, channel.id());
                statement.setString(4, sender.getUniqueId().toString());
                statement.setString(5, sender.getName());
                statement.setString(6, payload);
                statement.setString(7, plainText);
                statement.setString(8, mentioned);
                statement.setBoolean(9, mentionResult.everyoneMentioned());
                statement.executeUpdate();
                plugin.getDebugService().traceCrossServerPublish(sender, channel, settings.serverName());
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed to publish cross-server chat message: " + exception.getMessage());
            }
        });
    }

    public void saveShowcaseSnapshot(ShowcaseStoredSnapshot snapshot) {
        if (!isEnabled() || snapshot == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + snapshotTableName()
                         + " (snapshot_id, snapshot_type, server_id, server_name, sender_uuid, sender_name, payload_json, created_at) "
                         + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                         + "ON CONFLICT (snapshot_id) DO UPDATE SET "
                         + "snapshot_type = EXCLUDED.snapshot_type, "
                         + "server_id = EXCLUDED.server_id, "
                         + "server_name = EXCLUDED.server_name, "
                         + "sender_uuid = EXCLUDED.sender_uuid, "
                         + "sender_name = EXCLUDED.sender_name, "
                         + "payload_json = EXCLUDED.payload_json, "
                         + "created_at = EXCLUDED.created_at")) {
                statement.setString(1, snapshot.snapshotId());
                statement.setString(2, snapshot.snapshotType());
                statement.setString(3, snapshot.serverId());
                statement.setString(4, snapshot.serverName());
                statement.setString(5, snapshot.senderUuid() == null ? "" : snapshot.senderUuid().toString());
                statement.setString(6, snapshot.senderName());
                statement.setString(7, snapshot.payloadJson());
                statement.setTimestamp(8, Timestamp.from(snapshot.createdAt()));
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed to save showcase snapshot: " + exception.getMessage());
            }
        });
    }

    public ShowcaseStoredSnapshot findShowcaseSnapshot(String snapshotId, String snapshotType) throws SQLException {
        if (!isEnabled()) {
            return null;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT snapshot_id, snapshot_type, server_id, server_name, sender_uuid, sender_name, payload_json, created_at "
                     + "FROM " + snapshotTableName() + " WHERE snapshot_id = ? AND snapshot_type = ?")) {
            statement.setString(1, snapshotId);
            statement.setString(2, snapshotType);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                String senderUuid = resultSet.getString("sender_uuid");
                return new ShowcaseStoredSnapshot(
                    resultSet.getString("snapshot_id"),
                    resultSet.getString("snapshot_type"),
                    resultSet.getString("server_id"),
                    resultSet.getString("server_name"),
                    safeParseUuid(senderUuid),
                    resultSet.getString("sender_name"),
                    resultSet.getString("payload_json"),
                    createdAt == null ? Instant.EPOCH : createdAt.toInstant()
                );
            }
        }
    }

    public LogQueryResult queryLogs(LogQuery query) throws SQLException {
        if (!isEnabled()) {
            throw new IllegalStateException("Cross-server chat service is unavailable.");
        }
        LogQuery effectiveQuery = query == null ? new LogQuery(null, null, null, Instant.now().minus(Duration.ofHours(24)), 1, 10) : query;
        String table = settings.database().qualifiedTableName();
        try (Connection connection = dataSource.getConnection()) {
            LogQueryPlan initialPlan = buildLogQueryPlan(table, effectiveQuery, effectiveQuery.page());
            int totalCount = selectCount(connection, initialPlan);
            int totalPages = totalCount <= 0 ? 0 : (int) Math.ceil(totalCount / (double) effectiveQuery.limit());
            int effectivePage = totalPages <= 0 ? 1 : Math.min(effectiveQuery.page(), totalPages);
            LogQueryPlan plan = buildLogQueryPlan(table, effectiveQuery, effectivePage);
            return new LogQueryResult(
                effectivePage,
                effectiveQuery.limit(),
                totalCount,
                totalPages,
                selectEntries(connection, plan)
            );
        }
    }

    public PlayerColorPreference findPlayerColorPreference(UUID playerId) throws SQLException {
        if (!isEnabled()) {
            return null;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT mode, value FROM " + playerColorTableName() + " WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapPlayerColorPreference(resultSet.getString("mode"), resultSet.getString("value"));
            }
        }
    }

    public void savePlayerColorPreference(UUID playerId, PlayerColorPreference preference) throws SQLException {
        if (!isEnabled()) {
            throw new IllegalStateException("Cross-server chat service is unavailable.");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO " + playerColorTableName()
                     + " (player_uuid, mode, value, updated_at) VALUES (?, ?, ?, NOW()) "
                     + "ON CONFLICT (player_uuid) DO UPDATE SET "
                     + "mode = EXCLUDED.mode, value = EXCLUDED.value, updated_at = EXCLUDED.updated_at")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, normalizePlayerColorMode(preference));
            statement.setString(3, preference.value());
            statement.executeUpdate();
        }
    }

    public void removePlayerColorPreference(UUID playerId) throws SQLException {
        if (!isEnabled()) {
            throw new IllegalStateException("Cross-server chat service is unavailable.");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM " + playerColorTableName() + " WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    public int findMegaphoneBalance(UUID playerId) throws SQLException {
        if (!isEnabled()) {
            throw new IllegalStateException("Cross-server chat service is unavailable.");
        }
        try (Connection connection = dataSource.getConnection()) {
            return currentMegaphoneBalance(connection, playerId);
        }
    }

    public int adjustMegaphoneBalance(UUID playerId, String playerName, int delta) throws SQLException {
        if (!isEnabled()) {
            throw new IllegalStateException("Cross-server chat service is unavailable.");
        }
        String table = megaphoneBalanceTableName();
        String safePlayerName = normalizeMegaphonePlayerName(playerName);
        try (Connection connection = dataSource.getConnection()) {
            ensureMegaphoneRow(connection, table, playerId, safePlayerName);
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + table
                    + " SET player_name = CASE WHEN ? = '' THEN player_name ELSE ? END, "
                    + "amount = GREATEST(0, amount + ?), updated_at = NOW() "
                    + "WHERE player_uuid = ? RETURNING amount")) {
                statement.setString(1, safePlayerName);
                statement.setString(2, safePlayerName);
                statement.setInt(3, delta);
                statement.setString(4, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt("amount");
                    }
                }
            }
        }
        return 0;
    }

    public MegaphoneBalanceResult consumeMegaphoneBalance(UUID playerId, String playerName, int amount) throws SQLException {
        if (!isEnabled()) {
            throw new IllegalStateException("Cross-server chat service is unavailable.");
        }
        int cost = Math.max(0, amount);
        String table = megaphoneBalanceTableName();
        String safePlayerName = normalizeMegaphonePlayerName(playerName);
        try (Connection connection = dataSource.getConnection()) {
            ensureMegaphoneRow(connection, table, playerId, safePlayerName);
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + table
                    + " SET player_name = CASE WHEN ? = '' THEN player_name ELSE ? END, "
                    + "amount = amount - ?, updated_at = NOW() "
                    + "WHERE player_uuid = ? AND amount >= ? RETURNING amount")) {
                statement.setString(1, safePlayerName);
                statement.setString(2, safePlayerName);
                statement.setInt(3, cost);
                statement.setString(4, playerId.toString());
                statement.setInt(5, cost);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return new MegaphoneBalanceResult(true, resultSet.getInt("amount"));
                    }
                }
            }
            return new MegaphoneBalanceResult(false, currentMegaphoneBalance(connection, playerId));
        }
    }

    private HikariDataSource createDataSource(DatabaseSettings database) {
        return new HikariDataSource(createHikariConfig(database));
    }

    static HikariConfig createHikariConfig(DatabaseSettings database) {
        HikariConfig hikari = new HikariConfig();
        hikari.setDriverClassName(POSTGRES_DRIVER);
        hikari.setJdbcUrl(database.jdbcUrl());
        hikari.setUsername(database.username());
        hikari.setPassword(database.password());
        hikari.setPoolName("YmChat-CrossServer");
        hikari.setMaximumPoolSize(4);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(5000);
        hikari.setInitializationFailTimeout(5000);
        return hikari;
    }

    private void initializeSchema() throws SQLException {
        String table = settings.database().qualifiedTableName();
        String snapshotTable = snapshotTableName();
        String playerColorTable = playerColorTableName();
        String megaphoneTable = megaphoneBalanceTableName();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id BIGSERIAL PRIMARY KEY, "
                    + "server_id VARCHAR(80) NOT NULL, "
                    + "server_name VARCHAR(120) NOT NULL, "
                    + "channel_id VARCHAR(64) NOT NULL, "
                    + "sender_uuid VARCHAR(36) NOT NULL, "
                    + "sender_name VARCHAR(16) NOT NULL, "
                    + "component_json TEXT NOT NULL, "
                    + "plain_text TEXT NOT NULL DEFAULT '', "
                    + "mentioned_names TEXT NOT NULL DEFAULT '', "
                    + "everyone_mentioned BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()"
                    + ")"
            );
            statement.executeUpdate(
                "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS plain_text TEXT NOT NULL DEFAULT ''"
            );
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_ymchat_cross_server_messages_id ON " + table + " (id)"
            );
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_ymchat_cross_server_messages_created_at ON " + table + " (created_at DESC)"
            );
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_ymchat_cross_server_messages_channel_id ON " + table + " (channel_id)"
            );
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_ymchat_cross_server_messages_sender_name ON " + table + " (sender_name)"
            );
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + snapshotTable + " ("
                    + "snapshot_id VARCHAR(96) PRIMARY KEY, "
                    + "snapshot_type VARCHAR(32) NOT NULL, "
                    + "server_id VARCHAR(80) NOT NULL, "
                    + "server_name VARCHAR(120) NOT NULL, "
                    + "sender_uuid VARCHAR(36) NOT NULL, "
                    + "sender_name VARCHAR(16) NOT NULL, "
                    + "payload_json TEXT NOT NULL, "
                    + "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()"
                    + ")"
            );
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_ymchat_showcase_snapshots_created_at ON " + snapshotTable + " (created_at DESC)"
            );
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_ymchat_showcase_snapshots_type ON " + snapshotTable + " (snapshot_type)"
            );
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + playerColorTable + " ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY, "
                    + "mode VARCHAR(16) NOT NULL, "
                    + "value TEXT NOT NULL DEFAULT '', "
                    + "updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()"
                    + ")"
            );
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + megaphoneTable + " ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY, "
                    + "player_name VARCHAR(16) NOT NULL DEFAULT '', "
                    + "amount INTEGER NOT NULL DEFAULT 0, "
                    + "updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()"
                    + ")"
            );
        }
    }

    private void bootstrapCursor() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COALESCE(MAX(id), 0) FROM " + settings.database().qualifiedTableName());
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                lastSeenId = resultSet.getLong(1);
            }
        }
    }

    private void startPolling() {
        long period = Math.max(20L, settings.pollIntervalTicks());
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollSafely, period, period);
    }

    private void startPlainTextBackfill() {
        backfillTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, this::backfillPlainTextSafely);
    }

    private void pollSafely() {
        if (!isEnabled()) {
            return;
        }
        try {
            pollMessages();
            cleanupIfNeeded();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to poll cross-server chat messages: " + exception.getMessage());
        }
    }

    private void pollMessages() throws SQLException {
        List<RemoteChatMessage> pending = new ArrayList<>();
        long newestId = lastSeenId;
        String table = settings.database().qualifiedTableName();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, server_id, server_name, channel_id, sender_name, component_json, mentioned_names, everyone_mentioned "
                     + "FROM " + table + " WHERE id > ? AND server_id <> ? ORDER BY id ASC LIMIT ?")) {
            statement.setLong(1, lastSeenId);
            statement.setString(2, settings.serverId());
            statement.setInt(3, Math.max(1, settings.batchSize()));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long id = resultSet.getLong("id");
                    newestId = Math.max(newestId, id);
                    pending.add(new RemoteChatMessage(
                        id,
                        resultSet.getString("server_id"),
                        resultSet.getString("server_name"),
                        resultSet.getString("channel_id"),
                        resultSet.getString("sender_name"),
                        resultSet.getString("component_json"),
                        parseMentionedNames(resultSet.getString("mentioned_names")),
                        resultSet.getBoolean("everyone_mentioned")
                    ));
                }
            }
        }

        if (!pending.isEmpty()) {
            lastSeenId = newestId;
            for (RemoteChatMessage message : pending) {
                deliver(message);
            }
        }
    }

    private void deliver(RemoteChatMessage message) {
        ChatChannel channel = plugin.getChatConfig().findChannel(message.channelId());
        if (channel == null || !channel.crossServer()) {
            return;
        }
        if (channel.targetMode() != TargetMode.ALL) {
            return;
        }

        List<Player> recipients = new ArrayList<>(resolveRecipients(channel));
        if (recipients.isEmpty()) {
            return;
        }

        Component component = GSON.deserialize(message.componentJson());
        if (settings.showOrigin()) {
            String originPrefix = settings.originFormat()
                .replace("%origin_server%", message.serverName())
                .replace("%origin_id%", message.serverId());
            component = RichText.deserialize(originPrefix).append(component);
        }

        Component finalComponent = component;
        for (Player recipient : recipients) {
            plugin.getPlatformBridge().runForPlayer(recipient, () -> {
                if (recipient.isOnline() && plugin.isParticipating(recipient)) {
                    plugin.getPlatformBridge().sendMessage(recipient, finalComponent);
                }
            });
        }

        plugin.getMentionService().notifyRemoteMentions(
            message.senderName(),
            resolveMentionTargets(recipients, message.mentionedNames(), message.everyoneMentioned()),
            plugin.getPlatformBridge(),
            plugin.getChatConfig().mentionSettings()
        );
        plugin.getPlatformBridge().sendConsoleMessage(finalComponent);
    }

    private List<Player> resolveRecipients(ChatChannel channel) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        return online.stream()
            .filter(plugin::isParticipating)
            .filter(channel::canUse)
            .toList();
    }

    private List<Player> resolveMentionTargets(List<Player> recipients, List<String> mentionedNames, boolean everyoneMentioned) {
        if (everyoneMentioned) {
            return recipients;
        }
        if (mentionedNames.isEmpty()) {
            return List.of();
        }
        return recipients.stream()
            .filter(player -> mentionedNames.stream().anyMatch(name -> name.equalsIgnoreCase(player.getName())))
            .toList();
    }

    private List<String> parseMentionedNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<String> names = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        return names;
    }

    private void cleanupIfNeeded() throws SQLException {
        if (settings.retentionHours() <= 0) {
            return;
        }
        Instant now = Instant.now();
        if (Duration.between(lastCleanupAt, now).toMinutes() < 10) {
            return;
        }
        lastCleanupAt = now;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM " + settings.database().qualifiedTableName()
                     + " WHERE created_at < NOW() - (? * INTERVAL '1 hour')")) {
            statement.setInt(1, settings.retentionHours());
            statement.executeUpdate();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM " + snapshotTableName()
                     + " WHERE created_at < NOW() - (? * INTERVAL '1 hour')")) {
            statement.setInt(1, settings.retentionHours());
            statement.executeUpdate();
        }
    }

    private void backfillPlainTextSafely() {
        try {
            while (isEnabled()) {
                int updated = backfillPlainTextBatch();
                if (updated <= 0) {
                    return;
                }
            }
        } catch (SQLException exception) {
            if (isEnabled()) {
                plugin.getLogger().warning("Failed to backfill cross-server plain_text data: " + exception.getMessage());
            }
        }
    }

    private int backfillPlainTextBatch() throws SQLException {
        List<BackfillRow> rows = new ArrayList<>();
        String table = settings.database().qualifiedTableName();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                 "SELECT id, component_json FROM " + table
                     + " WHERE plain_text = '' OR plain_text IS NULL ORDER BY id ASC LIMIT ?")) {
            select.setInt(1, BACKFILL_BATCH_SIZE);
            try (ResultSet resultSet = select.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new BackfillRow(
                        resultSet.getLong("id"),
                        toPlainText(resultSet.getString("component_json"))
                    ));
                }
            }

            if (rows.isEmpty()) {
                return 0;
            }

            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET plain_text = ? WHERE id = ?")) {
                for (BackfillRow row : rows) {
                    update.setString(1, row.plainText());
                    update.setLong(2, row.id());
                    update.addBatch();
                }
                update.executeBatch();
            }
            return rows.size();
        }
    }

    private int selectCount(Connection connection, LogQueryPlan plan) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(plan.countSql())) {
            bindParameters(statement, plan.parameters());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
                return 0;
            }
        }
    }

    private List<LogEntry> selectEntries(Connection connection, LogQueryPlan plan) throws SQLException {
        List<LogEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(plan.selectSql())) {
            bindParameters(statement, plan.parameters());
            statement.setInt(plan.parameters().size() + 1, plan.limit());
            statement.setInt(plan.parameters().size() + 2, plan.offset());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp createdAt = resultSet.getTimestamp("created_at");
                    entries.add(new LogEntry(
                        resultSet.getLong("id"),
                        resultSet.getString("server_id"),
                        resultSet.getString("server_name"),
                        resultSet.getString("channel_id"),
                        resultSet.getString("sender_name"),
                        normalizePlainText(resultSet.getString("plain_text")),
                        createdAt == null ? Instant.EPOCH : createdAt.toInstant()
                    ));
                }
            }
        }
        return entries;
    }

    static LogQueryPlan buildLogQueryPlan(String table, LogQuery query, int page) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();

        if (query.since() != null) {
            where.append(" AND created_at >= ?");
            parameters.add(Timestamp.from(query.since()));
        }
        if (query.player() != null && !query.player().isBlank()) {
            where.append(" AND LOWER(sender_name) = LOWER(?)");
            parameters.add(query.player());
        }
        if (query.channel() != null && !query.channel().isBlank()) {
            where.append(" AND LOWER(channel_id) = LOWER(?)");
            parameters.add(query.channel());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append(" AND plain_text ILIKE ? ESCAPE '\\'");
            parameters.add("%" + escapeLikePattern(query.keyword()) + "%");
        }

        String countSql = "SELECT COUNT(*) FROM " + table + where;
        String selectSql = "SELECT id, server_id, server_name, channel_id, sender_name, plain_text, created_at"
            + " FROM " + table + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        int effectivePage = Math.max(1, page);
        return new LogQueryPlan(countSql, selectSql, List.copyOf(parameters), query.limit(), (effectivePage - 1) * query.limit());
    }

    static String toPlainText(String componentJson) {
        if (componentJson == null || componentJson.isBlank()) {
            return "";
        }
        try {
            return toPlainText(GSON.deserialize(componentJson));
        } catch (RuntimeException exception) {
            return normalizePlainText(componentJson);
        }
    }

    static String toPlainText(Component component) {
        if (component == null) {
            return "";
        }
        return normalizePlainText(PLAIN.serialize(component));
    }

    static String escapeLikePattern(String input) {
        return input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    private void bindParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object parameter = parameters.get(index);
            if (parameter instanceof Timestamp timestamp) {
                statement.setTimestamp(index + 1, timestamp);
            } else {
                statement.setObject(index + 1, parameter);
            }
        }
    }

    private static String normalizePlainText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String snapshotTableName() {
        return settings.database().qualifiedTableName() + "_snapshots";
    }

    private String playerColorTableName() {
        return playerColorTableName(settings.database().qualifiedTableName());
    }

    private String megaphoneBalanceTableName() {
        return megaphoneBalanceTableName(settings.database().qualifiedTableName());
    }

    private UUID safeParseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static String playerColorTableName(String qualifiedMessageTable) {
        return qualifiedMessageTable + "_player_colors";
    }

    static String megaphoneBalanceTableName(String qualifiedMessageTable) {
        return qualifiedMessageTable + "_megaphone_balances";
    }

    static String normalizePlayerColorMode(PlayerColorPreference preference) {
        return switch (preference.mode()) {
            case LEGACY -> "legacy";
            case RGB -> "rgb";
            case OFF -> "off";
            case PRESET -> throw new IllegalArgumentException("Cross-server storage does not support preset player colors.");
        };
    }

    static PlayerColorPreference mapPlayerColorPreference(String mode, String value) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        PlayerColorPreference.Mode parsedMode = PlayerColorPreference.Mode.parse(mode.toLowerCase(Locale.ROOT));
        return parsedMode == PlayerColorPreference.Mode.PRESET ? null : new PlayerColorPreference(parsedMode, value);
    }

    private void ensureMegaphoneRow(Connection connection, String table, UUID playerId, String playerName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + table + " (player_uuid, player_name, amount, updated_at) "
                + "VALUES (?, ?, 0, NOW()) ON CONFLICT (player_uuid) DO NOTHING")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, playerName);
            statement.executeUpdate();
        }
    }

    private int currentMegaphoneBalance(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT amount FROM " + megaphoneBalanceTableName() + " WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0;
                }
                return Math.max(0, resultSet.getInt("amount"));
            }
        }
    }

    private String normalizeMegaphonePlayerName(String playerName) {
        return playerName == null || playerName.isBlank() ? "" : playerName.trim();
    }

    @Override
    public synchronized void close() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        if (backfillTask != null) {
            backfillTask.cancel();
            backfillTask = null;
        }
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    public record LogQuery(
        String player,
        String channel,
        String keyword,
        Instant since,
        int page,
        int limit
    ) {

        public LogQuery {
            page = Math.max(1, page);
            limit = Math.max(1, limit);
        }
    }

    public record LogEntry(
        long id,
        String serverId,
        String serverName,
        String channelId,
        String senderName,
        String plainText,
        Instant createdAt
    ) {
    }

    public record LogQueryResult(
        int page,
        int limit,
        int totalCount,
        int totalPages,
        List<LogEntry> entries
    ) {
    }

    static record LogQueryPlan(
        String countSql,
        String selectSql,
        List<Object> parameters,
        int limit,
        int offset
    ) {
    }

    private record BackfillRow(long id, String plainText) {
    }

    private record RemoteChatMessage(
        long id,
        String serverId,
        String serverName,
        String channelId,
        String senderName,
        String componentJson,
        List<String> mentionedNames,
        boolean everyoneMentioned
    ) {
    }

    public record MegaphoneBalanceResult(boolean allowed, int balance) {
    }
}
