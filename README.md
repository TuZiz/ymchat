# YmChat

YmChat is a chat plugin scaffold that reads YAML rules and renders clickable / hoverable chat components on Paper, Spigot, and Folia.

## Included features

- Priority-based format selection
- Simple condition matching such as `player op`, `perm "node"` and `world "name"`
- Optional PlaceholderAPI placeholder resolution
- Optional LuckPerms prefix lookup for `%luckperms_prefix%`
- Click actions: `command`, `suggest`, `url`, `copy`
- Permission-aware inline color chat for public channels
- Fixed per-player public chat color with permission nodes and `/ymchat color`
- Fixed per-player name color with `/ymchat namecolor`
- Name color tokens: `{name_color}` and `{name_reset}`
- Megaphone channel based on the former `world` channel, with chat, title, and bossbar broadcast modes
- Persistent megaphone balances with `/ymchat megaphone give` and `/ymchat megaphone take`
- Configurable public chat message highlighting for keywords, price, quantity, coordinates, and time
- Multi-showcase public chat tokens: `/展示`, `[i]`, `:i:`, `[inv]`, `[ec]`, `[pos]`
- Cross-server inventory / ender chest snapshot previews through shared snapshot ids
- Cross-server chat log search via `/ymchat logs`
- Compact config layout with dedicated `channels/` and `colors.yml` resources
- Unified compatibility listener based on `AsyncPlayerChatEvent`
- Folia-safe player scheduling for render and delivery
- Config sections are split into `prefix`, `name`, and `message`
- Optional plain-text fallback via `Options.Force-Legacy`
- Channel system with per-channel routing and permissions
- Private message, reply, and social spy support
- Channel-specific format mapping via `Channels.List[].format`
- Dedicated private-message rule chain via `Private-Messages.Rules`
- Word filter system with `block` / `replace`, scope, channel limits, and regex support
- `@mention` highlight with sound and actionbar notification
- Persistent configured public chat and name color preferences
- Anti-spam checks for cooldown, max length, caps ratio, and duplicate messages
- Toggleable per-player debug trace
- Runtime commands: `/ymchat reload`, `/ymchat join`, `/ymchat leave`, `/ymchat status`

## Config shape

The default install keeps base feature settings in `config.yml`; shared chat/name color presets live in `colors.yml`; channel definitions live in `channels/`; GUI layouts stay in `gui/`, language packs stay in `lang/`, and player color preferences stay in `player-colors.yml`.

```yml
Options:
  Language: zh_cn
  Target: ALL
  Auto-Join: true
  Force-Legacy: false
  Debug: false

Files:
  Channels: 'channels'
  Colors: 'colors.yml'
  Highlights: 'rules.yml'
  Anti-Spam: 'rules.yml'
  Filter: 'rules.yml'
```

Default layout:

- `config.yml`: base options, private messages, mentions, and showcase settings
- `colors.yml`: inline color permissions plus shared fixed color values with separate chat/name permissions
- `rules.yml`: anti-spam and filter rules
- `highlights.yml`: public chat highlight rules
- `channels/global.yml`, `channels/cross-server.yml`, `channels/world.yml`, `channels/staff.yml`: one channel per file, using readable names instead of numeric prefixes; `world.yml` is now the default megaphone channel

Older installs that still point `Files` to the old `config/` folder are still readable. YmChat will log a migration hint and merge those files at runtime, but it will not overwrite, delete, or move existing server files automatically.

The original sample duplicated `prefix.player`, which is not valid YAML. Format rules live in each channel file under `Format`; after loading they are merged into `Channels.Formats`. The recommended shape is:

```yml
Formats:
  - condition: ~
    priority: 100
    msg:
      default-color: '&f'
      hover: '&fDate: %server_time_h:mm:ss a%'
    prefix:
      text: '...'
      hover: '...'
      command: '/tpa %player_name%'
    name:
      - condition: 'player op'
        text: '%luckperms_prefix%{name_color}%player_name%{name_reset}'
        suggest: '@%player_name% '
      - text: '%luckperms_prefix%{name_color}%player_name%{name_reset}'
    message:
      variants:
        - condition: 'perm "group.admin"'
          text: '&f: &c{message}'
        - text: '&f: {message}'
```

Condition expressions support:

- `player op`
- `perm "node"` / `!perm "node"`
- `world "world_name"`
- `placeholder "%some_placeholder%" equals "value"`
- `placeholder "%some_placeholder%" contains "value"`
- `placeholder "%some_placeholder%" startsWith "value"`
- `&&` and `||`

## New commands

- `/channel <id>` switch current chat channel
- `/msg <player> <message>` send a private message
- `/m <player> <message>` send a private message
- `/tell <player> <message>` send a private message
- `/w <player> <message>` send a private message
- `/reply <message>` reply to the last private message
- `/socialspy` toggle private message spy mode
- `/ymchat debug [on|off]` toggle personal debug output
- `/ymchat color` show current and available fixed public chat colors
- `/ymchat color <colorId|off|reset>` switch fixed public chat color
- `/ymchat namecolor` show current and available fixed name colors
- `/ymchat namecolor <colorId|off|reset>` switch fixed name color
- `/ymchat megaphone chat <message>` send a chat-bar megaphone broadcast
- `/ymchat megaphone title <message>` send a title megaphone broadcast
- `/ymchat megaphone bossbar <message>` send a bossbar megaphone broadcast
- `/ymchat megaphone give <player> <amount>` give megaphone balance
- `/ymchat megaphone take <player> <amount>` take megaphone balance
- `/ymchat logs [player:<name>] [channel:<id>] [keyword:<text>] [since:<1h|24h|7d>] [page:<n>] [limit:<n>]` search cross-server logs
- `/展示` show the item in your main hand to the current public chat channel
- `[inv]`, `[ec]`, and `[pos]` work directly in public chat messages for inventory, ender chest, and position showcase

## New config sections

- `config.yml`: locale, feature settings, mentions, and private-message rules
- `colors.yml`: shared fixed color values, chat color permissions, and name color permissions
- `rules.yml`: anti-spam and word filtering
- `highlights.yml`: public chat keyword and pattern highlighting
- `channels/*.yml`: available channels, aliases, permissions, target mode, and `format`
- `gui/showcase-preview.yml`: Shape-based read-only preview GUI for inventory and ender chest snapshots
- `gui/megaphone.yml`: Shape-based megaphone menu resource for chat, title, and bossbar broadcast entries

## Notes

- Current compile target is Paper API `1.20.6`, but runtime delivery now falls back to Spigot component sending when needed.
- Chat rendering is re-scheduled onto the player thread on Folia, and onto the normal server thread on Spigot/Paper.
- If PlaceholderAPI is not installed, unsupported placeholders are left as-is.
- 默认配置和 GUI 文件的展示文案保持内联，方便服主直接改；旧服已经写过的语言键引用仍会兼容解析。
- Fixed colors are defined once in `colors.yml` under `Colors.rgb-colors`. The default file includes `0` through `f`; removing an entry removes it from command selection.
- Players can only select color ids declared in `colors.yml`; arbitrary values are rejected until they are added there.
- Chat color switching is command-only. Use `/ymchat color` to view available values, `/ymchat color <colorId>` to switch, `/ymchat color off` to turn fixed color off, and `/ymchat color reset` to clear the saved preference.
- Each shared color has separate `chat-permission` and `name-permission` entries.
- Name color tokens only apply where the selected name section includes `{name_color}` and `{name_reset}`.
- Name color switching is command-only. Use `/ymchat namecolor` to view available values, `/ymchat namecolor <colorId>` to switch, `/ymchat namecolor off` to turn fixed name color off, and `/ymchat namecolor reset` to clear the saved preference.
- `/展示` and `[i]` still only read the main-hand item; `[inv]`, `[ec]`, and `[pos]` are also public-chat-only tokens.
- Inventory and ender chest showcase previews use `gui/showcase-preview.yml`, stay read-only, and can be opened across servers when cross-server chat storage is enabled.
