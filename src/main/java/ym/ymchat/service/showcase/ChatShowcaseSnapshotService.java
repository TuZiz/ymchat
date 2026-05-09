package ym.ymchat.service.showcase;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.command.CommandMessages;
import ym.ymchat.config.crossserver.CrossServerSettings;

public final class ChatShowcaseSnapshotService implements SnapshotGateway {

    private final YmChatPlugin plugin;
    private final ShowcasePreviewGuiService previewGuiService;
    private final ShowcaseSnapshotCodec codec;
    private final Map<String, ShowcaseStoredSnapshot> localSnapshots = new ConcurrentHashMap<>();

    public ChatShowcaseSnapshotService(YmChatPlugin plugin, ShowcasePreviewGuiService previewGuiService) {
        this.plugin = plugin;
        this.previewGuiService = previewGuiService;
        this.codec = new ShowcaseSnapshotCodec();
    }

    @Override
    public String createInventorySnapshot(ShowcaseSource source, long now) {
        cleanupExpiredLocal(Instant.ofEpochMilli(now));
        String snapshotId = UUID.randomUUID().toString();
        ShowcaseStoredSnapshot snapshot = new ShowcaseStoredSnapshot(
            snapshotId,
            "inventory",
            currentServerId(),
            currentServerName(),
            source.playerId(),
            source.playerName(),
            codec.encodeInventory(source.storageContents(), source.armorContents(), source.offHand()),
            Instant.ofEpochMilli(now)
        );
        storeSnapshot(snapshot);
        return snapshotId;
    }

    @Override
    public String createEnderChestSnapshot(ShowcaseSource source, long now) {
        cleanupExpiredLocal(Instant.ofEpochMilli(now));
        String snapshotId = UUID.randomUUID().toString();
        ShowcaseStoredSnapshot snapshot = new ShowcaseStoredSnapshot(
            snapshotId,
            "ender-chest",
            currentServerId(),
            currentServerName(),
            source.playerId(),
            source.playerName(),
            codec.encodeEnderChest(source.enderChestContents()),
            Instant.ofEpochMilli(now)
        );
        storeSnapshot(snapshot);
        return snapshotId;
    }

    public void openSnapshot(Player viewer, String rawType, String snapshotId) {
        SnapshotType snapshotType = SnapshotType.from(rawType);
        if (viewer == null || snapshotType == null || snapshotId == null || snapshotId.isBlank()) {
            CommandMessages.sendKey(plugin, viewer, "commands.ymchat.showcase.invalid");
            return;
        }

        cleanupExpiredLocal(Instant.now());
        ShowcaseStoredSnapshot localSnapshot = localSnapshots.get(snapshotId);
        if (localSnapshot != null && snapshotType.matches(localSnapshot.snapshotType()) && !isExpired(localSnapshot, Instant.now())) {
            openStoredSnapshot(viewer, localSnapshot);
            return;
        }

        if (!plugin.getCrossServerChatService().isEnabled()) {
            CommandMessages.sendKey(plugin, viewer, "commands.ymchat.showcase.expired");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ShowcaseStoredSnapshot storedSnapshot = plugin.getCrossServerChatService().findShowcaseSnapshot(snapshotId, snapshotType.storageName());
                plugin.getPlatformBridge().runForPlayer(viewer, () -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    if (storedSnapshot == null || isExpired(storedSnapshot, Instant.now())) {
                        CommandMessages.sendKey(plugin, viewer, "commands.ymchat.showcase.expired");
                        return;
                    }
                    localSnapshots.put(storedSnapshot.snapshotId(), storedSnapshot);
                    openStoredSnapshot(viewer, storedSnapshot);
                });
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed to load showcase snapshot: " + exception.getMessage());
                plugin.getPlatformBridge().runForPlayer(viewer, () -> {
                    if (viewer.isOnline()) {
                        CommandMessages.sendKey(plugin, viewer, "commands.ymchat.showcase.open-failed");
                    }
                });
            }
        });
    }

    private void openStoredSnapshot(Player viewer, ShowcaseStoredSnapshot snapshot) {
        SnapshotType snapshotType = SnapshotType.from(snapshot.snapshotType());
        if (snapshotType == null) {
            CommandMessages.sendKey(plugin, viewer, "commands.ymchat.showcase.invalid");
            return;
        }
        switch (snapshotType) {
            case INVENTORY -> previewGuiService.openInventory(viewer, snapshot, codec.decodeInventory(snapshot.payloadJson()));
            case ENDER_CHEST -> previewGuiService.openEnderChest(viewer, snapshot, codec.decodeEnderChest(snapshot.payloadJson()));
        }
    }

    private void storeSnapshot(ShowcaseStoredSnapshot snapshot) {
        localSnapshots.put(snapshot.snapshotId(), snapshot);
        if (plugin.getCrossServerChatService().isEnabled()) {
            plugin.getCrossServerChatService().saveShowcaseSnapshot(snapshot);
        }
    }

    private void cleanupExpiredLocal(Instant now) {
        if (localSnapshots.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ShowcaseStoredSnapshot> entry : localSnapshots.entrySet()) {
            if (isExpired(entry.getValue(), now)) {
                localSnapshots.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean isExpired(ShowcaseStoredSnapshot snapshot, Instant now) {
        int retentionHours = currentRetentionHours();
        if (retentionHours <= 0) {
            return false;
        }
        return snapshot.createdAt().plus(Duration.ofHours(retentionHours)).isBefore(now);
    }

    private int currentRetentionHours() {
        CrossServerSettings settings = plugin.getChatConfig() == null
            ? CrossServerSettings.defaults()
            : plugin.getChatConfig().crossServerSettings();
        return settings.retentionHours();
    }

    private String currentServerId() {
        if (plugin.getChatConfig() == null) {
            return "local";
        }
        return plugin.getChatConfig().crossServerSettings().serverId();
    }

    private String currentServerName() {
        if (plugin.getChatConfig() == null) {
            return plugin.getName();
        }
        return plugin.getChatConfig().crossServerSettings().serverName();
    }

    private enum SnapshotType {
        INVENTORY("inventory"),
        ENDER_CHEST("ender-chest");

        private final String storageName;

        SnapshotType(String storageName) {
            this.storageName = storageName;
        }

        public String storageName() {
            return storageName;
        }

        public boolean matches(String raw) {
            return storageName.equalsIgnoreCase(raw);
        }

        static SnapshotType from(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            for (SnapshotType value : values()) {
                if (value.storageName.equalsIgnoreCase(raw)) {
                    return value;
                }
            }
            return null;
        }
    }
}
