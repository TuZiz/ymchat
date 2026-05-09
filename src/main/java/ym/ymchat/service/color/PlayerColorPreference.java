package ym.ymchat.service.color;

public record PlayerColorPreference(PlayerColorPreference.Mode mode, String value) {

    public PlayerColorPreference {
        mode = mode == null ? Mode.OFF : mode;
        value = value == null ? "" : value.trim();
    }

    public static PlayerColorPreference legacy(String value) {
        return new PlayerColorPreference(Mode.LEGACY, value);
    }

    public static PlayerColorPreference preset(String value) {
        return new PlayerColorPreference(Mode.PRESET, value);
    }

    public static PlayerColorPreference rgb(String value) {
        return new PlayerColorPreference(Mode.RGB, value);
    }

    public static PlayerColorPreference off() {
        return new PlayerColorPreference(Mode.OFF, "");
    }

    public enum Mode {
        LEGACY,
        PRESET,
        RGB,
        OFF;

        public static Mode parse(String input) {
            if (input == null || input.isBlank()) {
                return OFF;
            }
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(input)) {
                    return mode;
                }
            }
            return OFF;
        }
    }
}
