package ym.ymchat.service.showcase;

import java.time.Instant;
import java.util.UUID;

public record ShowcaseStoredSnapshot(
    String snapshotId,
    String snapshotType,
    String serverId,
    String serverName,
    UUID senderUuid,
    String senderName,
    String payloadJson,
    Instant createdAt
) {

    public ShowcaseStoredSnapshot {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
