package ym.ymchat.service.chat;

import java.util.List;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.megaphone.MegaphoneMode;
import ym.ymchat.service.color.PlayerColorService;
import ym.ymchat.service.color.PublicChatColorService;
import ym.ymchat.service.showcase.ItemShowcaseService;
import ym.ymchat.service.text.RichText;

import ym.ymchat.service.showcase.PreparedShowcase;
public final class ChatMessageProcessor {

    private final YmChatPlugin plugin;

    public ChatMessageProcessor(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void process(Player sender, String rawMessage) {
        if (!sender.isOnline()) {
            return;
        }

        ChatChannel channel = plugin.getChannelService().resolveChannel(sender);
        var config = plugin.getChatConfig();
        var formatRule = config.firstMatching(sender, channel.id(), channel.format());
        String defaultColor = formatRule.messageOptions().defaultColor();

        PlayerColorService.ResolvedColor resolvedColor = plugin.getPlayerColorService().resolve(sender, config.colorChatSettings(), defaultColor);
        PublicChatColorService.PermissionAccess permissionAccess = new PublicChatColorService.PermissionAccess(
            sender.hasPermission(config.colorChatSettings().inlineSettings().legacyPermission()),
            sender.hasPermission(config.colorChatSettings().inlineSettings().formatPermission()),
            sender.hasPermission(config.colorChatSettings().inlineSettings().rgbPermission())
        );
        PublicChatColorService.PreparedPublicChatMessage prepared = plugin.getPublicChatColorService().prepare(
            rawMessage,
            defaultColor,
            resolvedColor,
            permissionAccess
        );

        TextFilterService.FilterResult filtered = plugin.getTextFilterService().apply(
            sender,
            prepared.visiblePlainText(),
            "public",
            channel.id(),
            config.filterSettings()
        );
        if (!filtered.allowed()) {
            sender.sendMessage(RichText.toLegacySectionString(filtered.message()));
            plugin.getDebugService().traceFilterBlock(sender, "public", channel.id(), filtered.describeHits());
            return;
        }

        AntiSpamService.CheckResult antiSpam = plugin.getAntiSpamService().check(sender, filtered.message(), config.antiSpamSettings());
        if (!antiSpam.allowed()) {
            sender.sendMessage(RichText.toLegacySectionString(antiSpam.message()));
            plugin.getDebugService().traceSpamBlock(sender, antiSpam.reason());
            return;
        }

        if (plugin.getMegaphoneService() != null && plugin.getMegaphoneService().shouldCapture(channel)) {
            plugin.getMegaphoneService().announce(sender, MegaphoneMode.CHAT, filtered.message());
            return;
        }

        List<Player> recipients = plugin.getPlatformBridge().snapshotRecipients(channel.targetMode(), sender).stream()
            .filter(player -> channel.permission() == null || channel.permission().isBlank() || player.hasPermission(channel.permission()))
            .toList();

        PublicChatColorService.PreparedPublicChatMessage finalMessage = filtered.modified()
            ? plugin.getPublicChatColorService().plain(filtered.message(), resolvedColor, defaultColor)
            : prepared;
        MentionService.MentionResult mentionResult = plugin.getMentionService().extractMentions(sender, filtered.message(), config.mentionSettings());
        PublicChatColorService.PreparedPublicChatMessage highlightedMessage = plugin.getPublicChatHighlightService().apply(
            finalMessage,
            filtered.message(),
            channel.id(),
            sender,
            config.publicChatHighlightSettings()
        );
        PreparedShowcase itemShowcase = plugin.getItemShowcaseService().prepare(
            sender,
            filtered.message(),
            config.itemShowcaseSettings()
        );
        if (itemShowcase.blocked()) {
            sender.sendMessage(RichText.toLegacySectionString(itemShowcase.blockedMessage()));
            return;
        }

        var messageComponent = plugin.getItemShowcaseService().apply(
            plugin.getMentionService().applyHighlights(highlightedMessage, config.mentionSettings(), mentionResult),
            itemShowcase
        );
        ChatRenderer.RenderedChat rendered = plugin.getChatRenderer().render(
            sender,
            channel,
            messageComponent,
            config
        );
        plugin.getItemShowcaseService().markUsed(sender, itemShowcase);

        for (Player recipient : recipients) {
            if (plugin.isParticipating(recipient)) {
                plugin.getPlatformBridge().runForPlayer(recipient, () -> {
                    if (recipient.isOnline() && plugin.isParticipating(recipient)) {
                        plugin.getPlatformBridge().sendMessage(recipient, rendered.component());
                    }
                });
            }
        }

        plugin.getMentionService().notifyMentions(sender, recipients, mentionResult, plugin.getPlatformBridge(), config.mentionSettings());
        plugin.getDebugService().traceChat(sender, channel, rendered.rule(), recipients, mentionResult, antiSpam.reason(), filtered.describeHits());
        plugin.getPlatformBridge().sendConsoleMessage(rendered.component());
        plugin.getCrossServerChatService().publish(
            sender,
            channel,
            plugin.getChatRenderer().renderCrossServer(sender, channel, messageComponent, config),
            mentionResult
        );
    }
}
