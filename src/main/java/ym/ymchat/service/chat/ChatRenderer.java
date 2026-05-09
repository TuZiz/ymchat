package ym.ymchat.service.chat;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import org.bukkit.entity.Player;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.ChatPluginConfig;
import ym.ymchat.config.chat.FormatRule;
import ym.ymchat.config.chat.MessageOptions;
import ym.ymchat.config.chat.SectionStyle;

import ym.ymchat.service.platform.DependencyBridge;
import ym.ymchat.service.text.RichText;
public final class ChatRenderer {

    private static final List<String> CROSS_SERVER_LOCAL_ONLY_TOKENS = List.of(
        "%multiverse-core_alias%",
        "%multiverse_alias%",
        "%world_alias%",
        "%world_name%",
        "%player_world%",
        "%origin_server%",
        "%origin_id%"
    );

    private final DependencyBridge dependencyBridge;

    public ChatRenderer(DependencyBridge dependencyBridge) {
        this.dependencyBridge = dependencyBridge;
    }

    public RenderedChat render(Player sender, ChatChannel channel, Component messageComponent, ChatPluginConfig config) {
        String formatId = channel.format() == null ? "" : channel.format();
        FormatRule rule = config.firstMatching(sender, channel.id(), formatId);
        Component result = Component.empty();

        if (config.showChannelDisplay() && channel.display() != null && !channel.display().isBlank()) {
            result = result.append(RichText.deserialize(resolve(sender, channel.display())));
        }

        SectionStyle prefixSection = rule.firstPrefixVariant(sender);
        if (prefixSection.hasText()) {
            result = result.append(buildStaticSection(sender, prefixSection));
        }

        SectionStyle nameSection = rule.firstNameVariant(sender);
        if (nameSection.hasText()) {
            result = result.append(buildStaticSection(sender, nameSection));
        }

        result = result.append(buildMessageSection(sender, messageComponent, rule.messageOptions(), rule.firstMessageVariant(sender)));
        return new RenderedChat(result, rule);
    }

    public Component renderCrossServer(Player sender, ChatChannel channel, Component messageComponent, ChatPluginConfig config) {
        String formatId = channel.format() == null ? "" : channel.format();
        FormatRule rule = config.firstMatching(sender, channel.id(), formatId);
        Component result = Component.empty();

        SectionStyle prefixSection = rule.firstPrefixVariant(sender);
        if (shouldIncludeCrossServerPrefix(prefixSection)) {
            result = result.append(buildStaticSection(sender, prefixSection));
        }

        SectionStyle nameSection = rule.firstNameVariant(sender);
        if (nameSection.hasText()) {
            result = result.append(buildStaticSection(sender, nameSection));
        }

        return result.append(buildMessageSection(sender, messageComponent, rule.messageOptions(), rule.firstMessageVariant(sender)));
    }

    public Component plainMessageComponent(String rawMessage, String defaultColor) {
        Style style = RichText.styleOf(defaultColor);
        return Component.text(rawMessage).style(style == null ? Style.empty() : style);
    }

    private Component buildStaticSection(Player player, SectionStyle section) {
        Component component = RichText.deserialize(resolve(player, section.text()));

        if (section.hover() != null && !section.hover().isBlank()) {
            component = component.hoverEvent(HoverEvent.showText(RichText.deserialize(resolve(player, section.hover()))));
        }

        ClickEvent clickEvent = createClickEvent(player, section.command(), section.suggest(), section.url(), section.copy());
        if (clickEvent != null) {
            component = component.clickEvent(clickEvent);
        }
        return component;
    }

    private Component buildMessageSection(Player player, Component messageComponent, MessageOptions messageOptions, SectionStyle selectedVariant) {
        String template = selectedVariant.text() == null || selectedVariant.text().isBlank() ? "{message}" : selectedVariant.text();
        String hover = firstNonBlank(selectedVariant.hover(), messageOptions.hover());
        ClickEvent clickEvent = createClickEvent(
            player,
            firstNonBlank(selectedVariant.command(), messageOptions.command()),
            firstNonBlank(selectedVariant.suggest(), messageOptions.suggest()),
            firstNonBlank(selectedVariant.url(), messageOptions.url()),
            firstNonBlank(selectedVariant.copy(), messageOptions.copy())
        );

        Component resolvedMessage = messageComponent;
        if (hover != null) {
            resolvedMessage = resolvedMessage.hoverEvent(HoverEvent.showText(RichText.deserialize(resolve(player, hover))));
        }
        if (clickEvent != null) {
            resolvedMessage = resolvedMessage.clickEvent(clickEvent);
        }

        if (!template.contains("{message}")) {
            Component rendered = RichText.deserialize(resolve(player, template));
            return applyInteractive(rendered, hover, clickEvent, player);
        }

        TextComponent.Builder builder = Component.text();
        int cursor = 0;
        while (cursor < template.length()) {
            int tokenIndex = template.indexOf("{message}", cursor);
            if (tokenIndex < 0) {
                builder.append(RichText.deserialize(resolve(player, template.substring(cursor))));
                break;
            }
            if (tokenIndex > cursor) {
                builder.append(RichText.deserialize(resolve(player, template.substring(cursor, tokenIndex))));
            }
            builder.append(resolvedMessage);
            cursor = tokenIndex + "{message}".length();
        }
        return builder.build();
    }

    private Component applyInteractive(Component component, String hover, ClickEvent clickEvent, Player player) {
        Component result = component;
        if (hover != null) {
            result = result.hoverEvent(HoverEvent.showText(RichText.deserialize(resolve(player, hover))));
        }
        if (clickEvent != null) {
            result = result.clickEvent(clickEvent);
        }
        return result;
    }

    private ClickEvent createClickEvent(Player player, String command, String suggest, String url, String copy) {
        if (command != null && !command.isBlank()) {
            return ClickEvent.runCommand(resolve(player, command));
        }
        if (suggest != null && !suggest.isBlank()) {
            return ClickEvent.suggestCommand(resolve(player, suggest));
        }
        if (url != null && !url.isBlank()) {
            return ClickEvent.openUrl(resolve(player, url));
        }
        if (copy != null && !copy.isBlank()) {
            return ClickEvent.copyToClipboard(resolve(player, copy));
        }
        return null;
    }

    private String resolve(Player player, String input) {
        return dependencyBridge.resolvePlaceholders(player, input);
    }

    static boolean shouldIncludeCrossServerPrefix(SectionStyle section) {
        if (section == null || !section.hasText()) {
            return false;
        }
        String lowered = section.text().toLowerCase(Locale.ROOT);
        for (String token : CROSS_SERVER_LOCAL_ONLY_TOKENS) {
            if (lowered.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    public record RenderedChat(Component component, FormatRule rule) {
    }
}
