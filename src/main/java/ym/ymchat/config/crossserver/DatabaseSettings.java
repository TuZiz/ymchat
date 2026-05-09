package ym.ymchat.config.crossserver;

import java.util.Locale;

public record DatabaseSettings(
    String host,
    int port,
    String database,
    String schema,
    String username,
    String password,
    String table,
    boolean ssl
) {

    private static final String IDENTIFIER_FALLBACK = "ymchat_cross_messages";

    public static DatabaseSettings defaults() {
        return new DatabaseSettings(
            "127.0.0.1",
            5432,
            "ymchat",
            "public",
            "postgres",
            "change-me",
            IDENTIFIER_FALLBACK,
            false
        );
    }

    public boolean isConfigured() {
        return host != null
            && !host.isBlank()
            && database != null
            && !database.isBlank()
            && username != null
            && !username.isBlank()
            && table != null
            && !table.isBlank();
    }

    public String jdbcUrl() {
        StringBuilder builder = new StringBuilder("jdbc:postgresql://")
            .append(host)
            .append(':')
            .append(port)
            .append('/')
            .append(database)
            .append("?ssl=")
            .append(ssl);
        String schemaName = sanitizedIdentifier(schema, "public");
        if (!schemaName.isBlank()) {
            builder.append("&currentSchema=").append(schemaName);
        }
        return builder.toString();
    }

    public String qualifiedTableName() {
        String schemaName = sanitizedIdentifier(schema, "public");
        String tableName = sanitizedIdentifier(table, IDENTIFIER_FALLBACK);
        return schemaName + "." + tableName;
    }

    private String sanitizedIdentifier(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("[a-z0-9_]+")) {
            return normalized;
        }
        return fallback;
    }
}
