# YmChat Project Context Token

Use this file as the first project-introspection token for AI agents working in this repository.

## Identity

- Project: YmChat
- Type: Minecraft Paper/Bukkit chat plugin
- Language: Java 21
- Build: Gradle Kotlin DSL
- Main plugin class: `ym.ymchat.YmChatPlugin`
- Main resources: `src/main/resources/plugin.yml`, `config.yml`, `colors.yml`, `rules.yml`, `highlights.yml`, `channels/*.yml`

## Fast Commands

```powershell
.\gradlew.bat compileJava
.\gradlew.bat compileTestJava
.\gradlew.bat test
.\gradlew.bat jar
```

The verified jar output is `build/libs/YmChat-1.0.0-SNAPSHOT.jar`.

## Source Map

- `src/main/java/ym/ymchat/YmChatPlugin.java`: plugin lifecycle, service wiring, command/listener registration.
- `src/main/java/ym/ymchat/command`: Bukkit command executors and tab completion.
- `src/main/java/ym/ymchat/config`: config loading entry points and top-level config object.
- `src/main/java/ym/ymchat/config/chat`: chat/channel/private-message/format config records.
- `src/main/java/ym/ymchat/config/color`: color chat config records.
- `src/main/java/ym/ymchat/config/crossserver`: PostgreSQL/cross-server config records.
- `src/main/java/ym/ymchat/config/filter`: text filter config records.
- `src/main/java/ym/ymchat/config/highlight`: public chat highlight config records.
- `src/main/java/ym/ymchat/config/megaphone`: megaphone config parser and records.
- `src/main/java/ym/ymchat/config/showcase`: item showcase config parser and preview layout records.
- `src/main/java/ym/ymchat/listener`: Bukkit event listeners.
- `src/main/java/ym/ymchat/service/chat`: public/private chat pipeline, rendering, mentions, filters, anti-spam.
- `src/main/java/ym/ymchat/service/color`: player chat color selection, fixed/RGB color permissions, local/cross-server color preference cache.
- `src/main/java/ym/ymchat/service/crossserver`: PostgreSQL-backed cross-server chat, logs, snapshots, colors, and megaphone balances.
- `src/main/java/ym/ymchat/service/debug`: debug tracing.
- `src/main/java/ym/ymchat/service/language`: YAML language bundle lookup and `lang:` reference resolution.
- `src/main/java/ym/ymchat/service/megaphone`: megaphone broadcast modes and balance store.
- `src/main/java/ym/ymchat/service/platform`: integration wrappers for PlaceholderAPI and platform-specific calls.
- `src/main/java/ym/ymchat/service/showcase`: item/inventory/ender chest/position showcase tokens and preview GUI.
- `src/main/java/ym/ymchat/service/text`: rich text, placeholders, and condition evaluation.

## High-Risk Invariants

- Keep Bukkit/Paper API calls on the main thread unless the existing code already snapshots data safely.
- Do not break `CrossServerChatService` shared PostgreSQL semantics. It owns schema setup, async polling, log query, snapshots, player colors, and megaphone balance operations.
- `MegaphoneBalanceStore` must keep dual-backend behavior: shared PostgreSQL when cross-server is enabled, local `megaphones.yml` fallback otherwise.
- `PlayerColorPreferenceCacheRepository` also has local/cross-server backend switching and async stale-write protection.
- Keep user-facing default resource text in YAML resources where possible. Java fallback strings should be stable and ASCII-safe unless a file already intentionally contains localized text.
- Avoid `Set-Content` for Java source edits on Windows; use `apply_patch` or UTF-8 without BOM writes.

## Recent Structure Decisions

- Services are grouped by domain under `service/chat`, `service/color`, `service/crossserver`, `service/megaphone`, `service/showcase`, `service/text`, etc.
- `ChatMessageProcessor` owns the chat processing workflow previously embedded in `YmChatPlugin`.
- Showcase runtime data is split into `ShowcaseType`, `ShowcaseSource`, `SnapshotGateway`, `PreparedShowcase`, and `ShowcaseReplacement`.
- `ChatConfigLoader` is the top-level config coordinator. `colors.yml` owns shared chat/name color definitions; `config/megaphone/MegaphoneConfigParser` and `config/showcase/ItemShowcaseConfigParser` own their feature-specific config parsing.
- `CrossServerChatService` is intentionally still large because it is the consistency boundary for cross-server storage.

## Search First

When unsure, use the file map in `AI_FILE_MAP.md` before scanning the whole repository.
