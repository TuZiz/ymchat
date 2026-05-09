package ym.ymchat.service.showcase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ym.ymchat.config.showcase.ItemShowcaseSettings;

import ym.ymchat.service.text.RichText;
import java.util.Locale;
public final class ItemShowcaseService {

    private final SnapshotGateway snapshotGateway;
    private final Map<UUID, Map<ShowcaseType, Long>> lastShowcaseAt = new ConcurrentHashMap<>();

    public ItemShowcaseService() {
        this(SnapshotGateway.noop());
    }

    public ItemShowcaseService(SnapshotGateway snapshotGateway) {
        this.snapshotGateway = snapshotGateway == null ? SnapshotGateway.noop() : snapshotGateway;
    }

    public PreparedShowcase prepare(Player player, String visiblePlainText, ItemShowcaseSettings settings) {
        return prepare(ShowcaseSource.of(player), visiblePlainText, settings, System.currentTimeMillis());
    }

    public PreparedShowcase prepare(
        ShowcaseSource source,
        String visiblePlainText,
        ItemShowcaseSettings settings,
        long now
    ) {
        ItemShowcaseSettings effectiveSettings = settings == null ? ItemShowcaseSettings.defaults() : settings;
        String message = visiblePlainText == null ? "" : visiblePlainText;
        if (!effectiveSettings.enabled()) {
            return PreparedShowcase.none();
        }

        List<RequestedToken> requestedTokens = collectRequestedTokens(message, effectiveSettings);
        if (requestedTokens.isEmpty()) {
            return PreparedShowcase.none();
        }

        List<ShowcaseReplacement> replacements = new ArrayList<>();
        for (RequestedToken requestedToken : requestedTokens) {
            PreparedReplacement preparedReplacement = switch (requestedToken.type()) {
                case ITEM -> prepareItem(source, effectiveSettings.item(), requestedToken, now);
                case INVENTORY -> prepareInventory(source, effectiveSettings.inventory(), requestedToken, now);
                case ENDER_CHEST -> prepareEnderChest(source, effectiveSettings.enderChest(), requestedToken, now);
                case POSITION -> preparePosition(source, effectiveSettings.position(), requestedToken, now);
            };
            if (preparedReplacement.blockedMessage() != null) {
                return PreparedShowcase.blocked(preparedReplacement.blockedMessage());
            }
            if (preparedReplacement.replacement() != null) {
                replacements.add(preparedReplacement.replacement());
            }
        }
        return replacements.isEmpty() ? PreparedShowcase.none() : PreparedShowcase.ready(replacements);
    }

    public Component apply(Component component, PreparedShowcase showcase) {
        if (component == null || showcase == null || !showcase.ready()) {
            return component == null ? Component.empty() : component;
        }
        Component result = component;
        for (ShowcaseReplacement replacement : showcase.replacements()) {
            result = result.replaceText(builder -> builder
                .match(replacement.tokenPattern())
                .times(replacement.maxReplacements())
                .replacement(replacement.replacement())
            );
        }
        return result;
    }

    public void markUsed(Player player, PreparedShowcase showcase) {
        if (player == null || showcase == null || !showcase.ready()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ShowcaseReplacement replacement : showcase.replacements()) {
            markUsed(player.getUniqueId(), replacement.type(), now);
        }
    }

    public void markUsed(UUID playerId, long now) {
        markUsed(playerId, ShowcaseType.ITEM, now);
    }

    public void markUsed(UUID playerId, ShowcaseType showcaseType, long now) {
        lastShowcaseAt.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
            .put(showcaseType, now);
    }

    public Component buildDisplayComponent(ItemStack itemStack, ItemShowcaseSettings settings) {
        ItemShowcaseSettings effectiveSettings = settings == null ? ItemShowcaseSettings.defaults() : settings;
        return buildItemComponent(itemStack, effectiveSettings.itemText());
    }

    public boolean containsAnyToken(String message, List<String> tokens) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        List<String> effectiveTokens = tokens == null || tokens.isEmpty()
            ? ItemShowcaseSettings.defaults().tokens()
            : tokens;
        for (String token : effectiveTokens) {
            if (token != null && !token.isBlank() && message.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private PreparedReplacement prepareItem(
        ShowcaseSource source,
        ItemShowcaseSettings.ItemSection section,
        RequestedToken requestedToken,
        long now
    ) {
        if (!section.enabled()) {
            return PreparedReplacement.skip();
        }
        String blocked = validateAccess(source, requestedToken.type(), section.permission(), section.bypassCooldownPermission(), section.cooldownMillis(), section.noPermissionMessage(), section.cooldownMessage(), now);
        if (blocked != null) {
            return PreparedReplacement.blocked(blocked);
        }

        ItemStack itemStack = ShowcaseSnapshotCodec.cloneItem(source.mainHand());
        if (isEmpty(itemStack)) {
            return PreparedReplacement.blocked(section.emptyMessage());
        }

        return PreparedReplacement.ready(new ShowcaseReplacement(
            requestedToken.type(),
            requestedToken.tokenPattern(),
            buildItemComponent(itemStack, section.text()),
            section.maxPerMessage()
        ));
    }

    private PreparedReplacement prepareInventory(
        ShowcaseSource source,
        ItemShowcaseSettings.SnapshotSection section,
        RequestedToken requestedToken,
        long now
    ) {
        if (!section.enabled()) {
            return PreparedReplacement.skip();
        }
        String blocked = validateAccess(source, requestedToken.type(), section.permission(), section.bypassCooldownPermission(), section.cooldownMillis(), section.noPermissionMessage(), section.cooldownMessage(), now);
        if (blocked != null) {
            return PreparedReplacement.blocked(blocked);
        }

        ItemStack[] storage = ShowcaseSnapshotCodec.cloneArray(source.storageContents());
        ItemStack[] armor = ShowcaseSnapshotCodec.cloneArray(source.armorContents());
        ItemStack offHand = ShowcaseSnapshotCodec.cloneItem(source.offHand());
        if (isAllEmpty(storage) && isAllEmpty(armor) && isEmpty(offHand)) {
            return PreparedReplacement.blocked(section.emptyMessage());
        }

        String snapshotId = snapshotGateway.createInventorySnapshot(source, now);
        return PreparedReplacement.ready(new ShowcaseReplacement(
            requestedToken.type(),
            requestedToken.tokenPattern(),
            buildSnapshotComponent(source, section.text(), section.hover(), snapshotId, "inventory"),
            section.maxPerMessage()
        ));
    }

    private PreparedReplacement prepareEnderChest(
        ShowcaseSource source,
        ItemShowcaseSettings.SnapshotSection section,
        RequestedToken requestedToken,
        long now
    ) {
        if (!section.enabled()) {
            return PreparedReplacement.skip();
        }
        String blocked = validateAccess(source, requestedToken.type(), section.permission(), section.bypassCooldownPermission(), section.cooldownMillis(), section.noPermissionMessage(), section.cooldownMessage(), now);
        if (blocked != null) {
            return PreparedReplacement.blocked(blocked);
        }

        ItemStack[] contents = ShowcaseSnapshotCodec.cloneArray(source.enderChestContents());
        if (isAllEmpty(contents)) {
            return PreparedReplacement.blocked(section.emptyMessage());
        }

        String snapshotId = snapshotGateway.createEnderChestSnapshot(source, now);
        return PreparedReplacement.ready(new ShowcaseReplacement(
            requestedToken.type(),
            requestedToken.tokenPattern(),
            buildSnapshotComponent(source, section.text(), section.hover(), snapshotId, "ender-chest"),
            section.maxPerMessage()
        ));
    }

    private PreparedReplacement preparePosition(
        ShowcaseSource source,
        ItemShowcaseSettings.PositionSection section,
        RequestedToken requestedToken,
        long now
    ) {
        if (!section.enabled()) {
            return PreparedReplacement.skip();
        }
        String blocked = validateAccess(source, requestedToken.type(), section.permission(), section.bypassCooldownPermission(), section.cooldownMillis(), section.noPermissionMessage(), section.cooldownMessage(), now);
        if (blocked != null) {
            return PreparedReplacement.blocked(blocked);
        }

        Component component = RichText.deserialize(applyPlaceholders(section.text(), source))
            .hoverEvent(buildHover(section.hover(), source))
            .clickEvent(ClickEvent.copyToClipboard(applyPlaceholders(section.copyText(), source)));
        return PreparedReplacement.ready(new ShowcaseReplacement(
            requestedToken.type(),
            requestedToken.tokenPattern(),
            component,
            section.maxPerMessage()
        ));
    }

    private Component buildSnapshotComponent(
        ShowcaseSource source,
        String text,
        List<String> hoverLines,
        String snapshotId,
        String snapshotType
    ) {
        Component base = RichText.deserialize(applyPlaceholders(text, source));
        if (hoverLines != null && !hoverLines.isEmpty()) {
            base = base.hoverEvent(buildHover(hoverLines, source));
        }
        if (snapshotId != null && !snapshotId.isBlank()) {
            base = base.clickEvent(ClickEvent.runCommand("/ymchat showcase open " + snapshotType + " " + snapshotId));
        }
        return base;
    }

    private Component buildHover(List<String> hoverLines, ShowcaseSource source) {
        TextComponent.Builder builder = Component.text();
        for (int index = 0; index < hoverLines.size(); index++) {
            if (index > 0) {
                builder.append(Component.newline());
            }
            builder.append(RichText.deserialize(applyPlaceholders(hoverLines.get(index), source)));
        }
        return builder.build();
    }

    private String applyPlaceholders(String input, ShowcaseSource source) {
        String value = input == null ? "" : input;
        return value
            .replace("%player_name%", source.playerName())
            .replace("%world%", source.worldName())
            .replace("%x%", String.valueOf(source.blockX()))
            .replace("%y%", String.valueOf(source.blockY()))
            .replace("%z%", String.valueOf(source.blockZ()));
    }

    private String validateAccess(
        ShowcaseSource source,
        ShowcaseType showcaseType,
        String permission,
        String bypassPermission,
        long cooldownMillis,
        String noPermissionMessage,
        String cooldownMessage,
        long now
    ) {
        if (!hasPermission(source, permission)) {
            return noPermissionMessage;
        }
        if (!hasPermission(source, bypassPermission)) {
            long remainingMillis = remainingCooldown(source.playerId(), showcaseType, cooldownMillis, now);
            if (remainingMillis > 0L) {
                return formatCooldownMessage(cooldownMessage, remainingMillis);
            }
        }
        return null;
    }

    private List<RequestedToken> collectRequestedTokens(String message, ItemShowcaseSettings settings) {
        List<RequestedToken> requested = new ArrayList<>();
        collectRequestedToken(requested, message, ShowcaseType.ITEM, settings.item().tokens());
        collectRequestedToken(requested, message, ShowcaseType.INVENTORY, settings.inventory().tokens());
        collectRequestedToken(requested, message, ShowcaseType.ENDER_CHEST, settings.enderChest().tokens());
        collectRequestedToken(requested, message, ShowcaseType.POSITION, settings.position().tokens());
        requested.sort(Comparator.comparingInt(RequestedToken::firstIndex));
        return requested;
    }

    private void collectRequestedToken(List<RequestedToken> requested, String message, ShowcaseType type, List<String> tokens) {
        int firstIndex = firstTokenIndex(message, tokens);
        if (firstIndex >= 0) {
            requested.add(new RequestedToken(type, firstIndex, buildTokenPattern(tokens)));
        }
    }

    private int firstTokenIndex(String message, List<String> tokens) {
        int firstIndex = -1;
        if (message == null || tokens == null) {
            return firstIndex;
        }
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            int index = message.indexOf(token);
            if (index >= 0 && (firstIndex < 0 || index < firstIndex)) {
                firstIndex = index;
            }
        }
        return firstIndex;
    }

    private Component buildLabelComponent(ItemStack itemStack, String template) {
        String normalizedTemplate = (template == null || template.isBlank()
            ? ItemShowcaseSettings.defaults().itemText()
            : template).replace("{amount}", String.valueOf(Math.max(1, itemStack.getAmount())));
        Component itemName = displayName(itemStack);
        if (!normalizedTemplate.contains("{item_name}")) {
            return RichText.deserialize(normalizedTemplate);
        }

        TextComponent.Builder builder = Component.text();
        int cursor = 0;
        while (cursor < normalizedTemplate.length()) {
            int tokenIndex = normalizedTemplate.indexOf("{item_name}", cursor);
            if (tokenIndex < 0) {
                builder.append(RichText.deserialize(normalizedTemplate.substring(cursor)));
                break;
            }
            if (tokenIndex > cursor) {
                builder.append(RichText.deserialize(normalizedTemplate.substring(cursor, tokenIndex)));
            }
            builder.append(itemName);
            cursor = tokenIndex + "{item_name}".length();
        }
        return builder.build();
    }

    private Component buildItemComponent(ItemStack itemStack, String template) {
        ItemStack snapshot = itemStack == null ? new ItemStack(Material.AIR) : itemStack.clone();
        Component label = buildLabelComponent(snapshot, template);
        try {
            return label.hoverEvent(snapshot.asHoverEvent(UnaryOperator.identity()));
        } catch (RuntimeException | LinkageError ignored) {
            return label;
        }
    }

    private Component displayName(ItemStack itemStack) {
        try {
            return itemStack.displayName();
        } catch (RuntimeException | LinkageError ignored) {
            try {
                return Component.translatable(itemStack.translationKey());
            } catch (RuntimeException | LinkageError ignoredAgain) {
                return Component.text(humanReadableMaterialName(itemStack.getType()));
            }
        }
    }

    private String humanReadableMaterialName(Material material) {
        return material == null ? "Item" : material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null
            || itemStack.getType() == Material.AIR
            || itemStack.getAmount() <= 0;
    }

    private boolean isAllEmpty(ItemStack[] contents) {
        if (contents == null || contents.length == 0) {
            return true;
        }
        for (ItemStack itemStack : contents) {
            if (!isEmpty(itemStack)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPermission(ShowcaseSource source, String permission) {
        return permission == null || permission.isBlank() || source.hasPermission(permission);
    }

    private long remainingCooldown(UUID playerId, ShowcaseType showcaseType, long cooldownMillis, long now) {
        if (cooldownMillis <= 0L) {
            return 0L;
        }
        Long lastUsed = lastShowcaseAt
            .getOrDefault(playerId, Map.of())
            .get(showcaseType);
        if (lastUsed == null) {
            return 0L;
        }
        return Math.max(0L, cooldownMillis - (now - lastUsed));
    }

    private String formatCooldownMessage(String message, long remainingMillis) {
        long remainingSeconds = Math.max(1L, (long) Math.ceil(remainingMillis / 1000D));
        return message
            .replace("%remaining_millis%", String.valueOf(remainingMillis))
            .replace("%remaining_seconds%", String.valueOf(remainingSeconds));
    }

    private Pattern buildTokenPattern(List<String> tokens) {
        List<String> effectiveTokens = tokens == null || tokens.isEmpty()
            ? ItemShowcaseSettings.defaults().tokens()
            : tokens;
        String pattern = effectiveTokens.stream()
            .filter(token -> token != null && !token.isBlank())
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
        if (pattern.isBlank()) {
            pattern = Pattern.quote("[item]") + "|" + Pattern.quote("[i]") + "|" + Pattern.quote(":i:");
        }
        return Pattern.compile(pattern);
    }

    private record RequestedToken(
        ShowcaseType type,
        int firstIndex,
        Pattern tokenPattern
    ) {
    }

    private record PreparedReplacement(
        String blockedMessage,
        ShowcaseReplacement replacement
    ) {

        static PreparedReplacement blocked(String message) {
            return new PreparedReplacement(message, null);
        }

        static PreparedReplacement ready(ShowcaseReplacement replacement) {
            return new PreparedReplacement(null, replacement);
        }

        static PreparedReplacement skip() {
            return new PreparedReplacement(null, null);
        }
    }
}