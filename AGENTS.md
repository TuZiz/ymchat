# Agent Instructions For YmChat

Start every non-trivial task by reading:

1. `PROJECT_CONTEXT.md`
2. `AI_FILE_MAP.md`

For file-location-only tasks, use `.claude/agents/ymchat-file-finder.md` as the routing prompt.

## Project Defaults

- This is a Java 21 Minecraft Paper/Bukkit chat plugin.
- Prefer narrow, source-traced changes.
- Preserve Bukkit/Paper main-thread safety.
- Run `.\gradlew.bat test` after code changes when feasible.

## Important Boundaries

- `CrossServerChatService` is the cross-server consistency boundary. Do not broadly rewrite it without focused tests.
- `MegaphoneBalanceStore` must keep PostgreSQL-backed storage plus local `megaphones.yml` fallback.
- `PlayerColorPreferenceCacheRepository` must keep async stale-write protection.
- Avoid Windows encoding damage: do not use PowerShell `Set-Content` for Java source rewrites.

## Fast Routing

- Chat pipeline: `service/chat`
- Colors: `service/color`
- Cross-server: `service/crossserver`
- Megaphone: `service/megaphone`
- Showcase: `service/showcase`
- Text/placeholders/conditions: `service/text`
- Config/resources: `config` root for loaders, `config/*` subpackages for feature records, and `src/main/resources`
