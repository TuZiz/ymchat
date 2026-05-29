package ym.ymchat.service.color;

import java.util.UUID;

public interface PlayerColorPreferenceRepository {

    PlayerColorPreference get(UUID playerId, ColorScope scope);

    void save(UUID playerId, ColorScope scope, PlayerColorPreference preference);

    void remove(UUID playerId, ColorScope scope);

    default PlayerColorPreference get(UUID playerId) {
        return get(playerId, ColorScope.CHAT);
    }

    default void save(UUID playerId, PlayerColorPreference preference) {
        save(playerId, ColorScope.CHAT, preference);
    }

    default void remove(UUID playerId) {
        remove(playerId, ColorScope.CHAT);
    }
}
