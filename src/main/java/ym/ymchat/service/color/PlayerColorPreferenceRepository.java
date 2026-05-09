package ym.ymchat.service.color;

import java.util.UUID;

public interface PlayerColorPreferenceRepository {

    PlayerColorPreference get(UUID playerId);

    void save(UUID playerId, PlayerColorPreference preference);

    void remove(UUID playerId);
}
