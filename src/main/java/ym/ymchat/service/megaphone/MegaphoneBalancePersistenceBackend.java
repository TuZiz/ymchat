package ym.ymchat.service.megaphone;

import java.util.Map;
import java.util.UUID;

interface MegaphoneBalancePersistenceBackend {

    default void reload(String fileName) throws Exception {
    }

    int load(UUID playerId) throws Exception;

    Map<UUID, Integer> snapshotBalances() throws Exception;

    int adjust(UUID playerId, String playerName, int delta) throws Exception;

    MegaphoneBalanceStore.ConsumeResult consume(UUID playerId, String playerName, int amount) throws Exception;

    String description();
}
