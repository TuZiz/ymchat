package ym.ymchat.service.color;

import java.util.UUID;

interface PlayerColorPreferencePersistenceBackend {

    PlayerColorPreference load(UUID playerId, ColorScope scope) throws Exception;

    void save(UUID playerId, ColorScope scope, PlayerColorPreference preference) throws Exception;

    void remove(UUID playerId, ColorScope scope) throws Exception;

    String description();
}
