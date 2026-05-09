package ym.ymchat.service.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.ChatPluginConfig;

import ym.ymchat.service.language.LanguageService;
public final class ChannelService {

    private final Map<UUID, String> activeChannels = new ConcurrentHashMap<>();
    private final ConfigAccessor accessor;
    private final LanguageService languageService;

    public ChannelService(ConfigAccessor accessor, LanguageService languageService) {
        this.accessor = accessor;
        this.languageService = languageService;
    }

    public ChatChannel resolveChannel(Player player) {
        ChatPluginConfig config = accessor.chatConfig();
        String selected = activeChannels.get(player.getUniqueId());
        ChatChannel channel = selected == null ? null : config.findChannel(selected);
        if (channel != null && channel.canUse(player)) {
            return channel;
        }
        ChatChannel fallback = config.defaultChannel();
        activeChannels.put(player.getUniqueId(), fallback.id());
        return fallback;
    }

    public String switchChannel(Player player, String requestedChannel) {
        ChatChannel channel = accessor.chatConfig().findChannel(requestedChannel);
        if (channel == null) {
            return languageService.get("service.channel.unknown", "channel", requestedChannel);
        }
        if (!channel.canUse(player)) {
            return languageService.get("service.channel.no-permission", "channel", commandLabel(channel));
        }
        activeChannels.put(player.getUniqueId(), channel.id());
        return languageService.get("service.channel.switched", "channel", commandLabel(channel));
    }

    public List<String> availableChannelIds(Player player) {
        return accessor.chatConfig().channels().stream()
            .filter(channel -> channel.canUse(player))
            .map(this::commandLabel)
            .toList();
    }

    public String describeChannel(ChatChannel channel) {
        return channel == null ? languageService.get("common.none") : commandLabel(channel);
    }

    private String commandLabel(ChatChannel channel) {
        for (String alias : channel.aliases()) {
            if (alias.chars().anyMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN)) {
                return alias;
            }
        }
        return channel.id();
    }

    public interface ConfigAccessor {
        ChatPluginConfig chatConfig();
    }
}
