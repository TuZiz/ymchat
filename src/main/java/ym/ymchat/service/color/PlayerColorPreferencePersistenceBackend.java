package ym.ymchat.service.color;

import java.util.UUID;

interface PlayerColorPreferencePersistenceBackend {

    PlayerColorPreference load(UUID playerId) throws Exception;

    void save(UUID playerId, PlayerColorPreference preference) throws Exception;

    void remove(UUID playerId) throws Exception;

    String description();
}
