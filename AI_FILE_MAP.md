# YmChat AI File Map

This map is optimized for AI agents that need to find the right file quickly.

## Start Here

- Plugin lifecycle and dependency graph: `src/main/java/ym/ymchat/YmChatPlugin.java`
- Project context token: `PROJECT_CONTEXT.md`
- This file map: `AI_FILE_MAP.md`
- Build config: `build.gradle.kts`
- Plugin metadata/commands/permissions: `src/main/resources/plugin.yml`
- Tests: `src/test/java`

## Feature Routing

### Chat Pipeline

- Entry after Bukkit event: `service/chat/ChatMessageProcessor.java`
- Rendering final component: `service/chat/ChatRenderer.java`
- Channels: `service/chat/ChannelService.java`, `config/chat/ChatChannel.java`
- Public highlight rules: `service/chat/PublicChatHighlightService.java`, `config/highlight/PublicChatHighlightSettings.java`
- Mentions: `service/chat/MentionService.java`, `config/chat/MentionSettings.java`
- Filters: `service/chat/TextFilterService.java`, `config/filter/FilterSettings.java`, `config/filter/FilterRule.java`
- Anti-spam: `service/chat/AntiSpamService.java`, `config/chat/AntiSpamSettings.java`
- Private messages: `service/chat/PrivateMessageService.java`, `config/chat/PrivateMessageSettings.java`, `command/MessageCommand.java`, `command/ReplyCommand.java`, `command/SocialSpyCommand.java`

### Player Color

- Runtime resolution: `service/color/PlayerColorService.java`
- Public chat formatting parser: `service/color/PublicChatColorService.java`
- Color code helpers: `service/color/ColorCodeUtil.java`
- Preference model: `service/color/PlayerColorPreference.java`
- Local/cross-server cache: `service/color/PlayerColorPreferenceCacheRepository.java`
- Local YAML store: `service/color/PlayerColorPreferenceStore.java`
- Lifecycle preload/clear: `listener/PlayerColorLifecycleListener.java`
- Config: `config/color/ColorChatSettings.java`, `config/color/FixedColorSettings.java`, `config/color/InlineColorSettings.java`, `config/color/ColorPreset.java`

### Cross-Server

- Main consistency boundary: `service/crossserver/CrossServerChatService.java`
- Log query formatting/parsing: `service/crossserver/CrossServerLogService.java`
- Config: `config/crossserver/CrossServerSettings.java`, `config/crossserver/CrossServerLogSettings.java`, `config/crossserver/DatabaseSettings.java`
- Tests: `src/test/java/ym/ymchat/service/CrossServerChatServiceTest.java`, `CrossServerLogServiceTest.java`

Do not aggressively split or rewrite `CrossServerChatService` without focused tests. It owns PostgreSQL schema setup, polling, snapshots, player color persistence, and megaphone balance persistence.

### Megaphone

- Broadcast orchestration: `service/megaphone/MegaphoneService.java`
- Balance store and backend switching: `service/megaphone/MegaphoneBalanceStore.java`
- Persistence backend interface: `service/megaphone/MegaphoneBalancePersistenceBackend.java`
- Boss bar animation: `service/megaphone/BossBarTextAnimator.java`
- Lifecycle preload/clear: `listener/MegaphoneBalanceLifecycleListener.java`
- Command surface: `command/YmChatCommand.java`
- Config parser: `config/megaphone/MegaphoneConfigParser.java`
- Config model: `config/megaphone/MegaphoneSettings.java`, `config/megaphone/MegaphoneMode.java`
- GUI resource: `src/main/resources/gui/megaphone.yml`

### Item Showcase

- Token detection and replacement: `service/showcase/ItemShowcaseService.java`
- Preview GUI: `service/showcase/ShowcasePreviewGuiService.java`
- Snapshot storage/gateway: `service/showcase/ChatShowcaseSnapshotService.java`
- Snapshot codec: `service/showcase/ShowcaseSnapshotCodec.java`
- Runtime models: `ShowcaseType.java`, `ShowcaseSource.java`, `SnapshotGateway.java`, `PreparedShowcase.java`, `ShowcaseReplacement.java`, `ShowcaseStoredSnapshot.java`
- Config parser: `config/showcase/ItemShowcaseConfigParser.java`
- Config model: `config/showcase/ItemShowcaseSettings.java`
- Layout config: `config/showcase/ShowcasePreviewLayout.java`, `config/showcase/ShowcasePreviewLayoutLoader.java`
- Command: `command/ItemShowcaseCommand.java`
- Listener: `listener/ShowcasePreviewListener.java`
- GUI resource: `src/main/resources/gui/showcase-preview.yml`

### Config And Resources

- Main config merge/load: `config/ChatConfigFileLoader.java`
- Top-level parsing: `config/ChatConfigLoader.java`
- Main config object: `config/ChatPluginConfig.java`
- Chat config records: `config/chat`
- Color config records: `config/color`
- Cross-server config records: `config/crossserver`
- Filter config records: `config/filter`
- Highlight config records: `config/highlight`
- Megaphone config parser and records: `config/megaphone`
- Showcase config parser and records: `config/showcase`
- Defaults/resources:
  - `src/main/resources/config.yml`
  - `src/main/resources/colors.yml`
  - `src/main/resources/rules.yml`
  - `src/main/resources/highlights.yml`
  - `src/main/resources/channels/*.yml`
  - `src/main/resources/lang/zh_cn.yml`
  - `src/main/resources/lang/en_us.yml`

### Text, Placeholder, Conditions

- Rich text color conversion: `service/text/RichText.java`
- PlaceholderAPI bridge and built-ins: `service/text/PlaceholderResolver.java`
- Condition DSL: `service/text/ConditionEvaluator.java`
- Platform wrappers: `service/platform/DependencyBridge.java`, `service/platform/PlatformBridge.java`
- Language lookup: `service/language/LanguageService.java`

### Commands

- Main admin/player command: `command/YmChatCommand.java`
- Channel switch command: `command/ChannelCommand.java`
- Private message command: `command/MessageCommand.java`
- Reply command: `command/ReplyCommand.java`
- Social spy command: `command/SocialSpyCommand.java`
- Item showcase command: `command/ItemShowcaseCommand.java`
- Shared command messages: `command/CommandMessages.java`

## Test Routing

Tests are under `src/test/java`.

- Config tests: `config/*Test.java`
- Command tests: `command/YmChatCommandTest.java`
- Cross-server tests use package declarations under `ym.ymchat.service.crossserver`.
- Megaphone balance tests use package declarations under `ym.ymchat.service.megaphone`.
- Color preference cache tests use package declarations under `ym.ymchat.service.color`.
- Some test file paths still live under `src/test/java/ym/ymchat/service` even when the declared package is more specific. Trust the package declaration and imports, not only the folder name.

## Keyword Lookup

- "chat message", "public chat", "format", "render": `ChatMessageProcessor`, `ChatRenderer`
- "mention", "@player", "everyone": `MentionService`
- "color", "RGB", "legacy color", "preset": `PlayerColorService`, `PublicChatColorService`
- "cross server", "PostgreSQL", "logs", "polling": `CrossServerChatService`, `CrossServerLogService`
- "megaphone", "horn", "bossbar", "title", "balance": `MegaphoneService`, `MegaphoneBalanceStore`
- "showcase", "[i]", "[inv]", "[ec]", "[pos]": `ItemShowcaseService`, `ChatShowcaseSnapshotService`
- "language", "lang:", "zh_cn": `LanguageService`, `lang/*.yml`
- "config split", "defaults", "resource contract": `ChatConfigFileLoader`, `ChatConfigLoader`, `LocalizationResourceTest`

## Suggested Search Commands

```powershell
Get-ChildItem -Path src -Recurse -Filter *.java | Select-String -Pattern 'ChatMessageProcessor'
Get-ChildItem -Path src\main\resources -Recurse -File | Select-String -Pattern 'Megaphone'
Get-ChildItem -Path src\test\java -Recurse -Filter *.java | Select-String -Pattern 'CrossServerChatService'
```

Prefer targeted `Get-ChildItem` plus `Select-String` on this Windows workspace if `rg.exe` is unavailable or denied.
