package ym.ymchat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ym.ymchat.command.ChannelCommand;
import ym.ymchat.command.ItemShowcaseCommand;
import ym.ymchat.command.MessageCommand;
import ym.ymchat.command.ReplyCommand;
import ym.ymchat.command.SocialSpyCommand;
import ym.ymchat.command.YmChatCommand;
import ym.ymchat.config.ChatConfigFileLoader;
import ym.ymchat.config.ChatConfigLoader;
import ym.ymchat.config.ChatPluginConfig;
import ym.ymchat.service.chat.AntiSpamService;
import ym.ymchat.service.chat.ChatMessageProcessor;
import ym.ymchat.service.chat.ChannelService;
import ym.ymchat.service.chat.FilterCloudWordService;
import ym.ymchat.service.showcase.ChatShowcaseSnapshotService;
import ym.ymchat.service.chat.ChatRenderer;
import ym.ymchat.service.crossserver.CrossServerChatService;
import ym.ymchat.service.debug.DebugService;
import ym.ymchat.service.platform.DependencyBridge;
import ym.ymchat.service.language.LanguageService;
import ym.ymchat.service.showcase.ItemShowcaseService;
import ym.ymchat.service.chat.MentionService;
import ym.ymchat.service.megaphone.MegaphoneBalanceStore;
import ym.ymchat.service.megaphone.MegaphoneService;
import ym.ymchat.service.crossserver.CrossServerLogService;
import ym.ymchat.service.platform.PlatformBridge;
import ym.ymchat.service.color.ColorScope;
import ym.ymchat.service.color.PlayerColorPreferenceCacheRepository;
import ym.ymchat.service.color.PlayerColorPreferenceStore;
import ym.ymchat.service.color.PlayerColorService;
import ym.ymchat.service.chat.PrivateMessageService;
import ym.ymchat.service.color.PublicChatColorService;
import ym.ymchat.service.chat.PublicChatHighlightService;
import ym.ymchat.service.showcase.ShowcasePreviewGuiService;
import ym.ymchat.service.chat.TextFilterService;

public final class YmChatPlugin extends JavaPlugin {

    private final Set<UUID> optedInPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> optedOutPlayers = ConcurrentHashMap.newKeySet();

    private ChatPluginConfig chatConfig;
    private ChatConfigFileLoader chatConfigFileLoader;
    private LanguageService languageService;
    private DependencyBridge dependencyBridge;
    private ChatRenderer chatRenderer;
    private PlatformBridge platformBridge;
    private ChannelService channelService;
    private AntiSpamService antiSpamService;
    private MentionService mentionService;
    private PrivateMessageService privateMessageService;
    private DebugService debugService;
    private TextFilterService textFilterService;
    private FilterCloudWordService filterCloudWordService;
    private CrossServerChatService crossServerChatService;
    private PlayerColorPreferenceStore playerColorPreferenceStore;
    private PlayerColorPreferenceCacheRepository playerColorPreferenceRepository;
    private PlayerColorService playerColorService;
    private PublicChatColorService publicChatColorService;
    private PublicChatHighlightService publicChatHighlightService;
    private ShowcasePreviewGuiService showcasePreviewGuiService;
    private ChatShowcaseSnapshotService chatShowcaseSnapshotService;
    private ItemShowcaseService itemShowcaseService;
    private CrossServerLogService crossServerLogService;
    private MegaphoneBalanceStore megaphoneBalanceStore;
    private MegaphoneService megaphoneService;
    private ChatMessageProcessor chatMessageProcessor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang/zh_cn.yml", false);
        saveResource("lang/en_us.yml", false);
        saveResource("channels/global.yml", false);
        saveResource("channels/cross-server.yml", false);
        saveResource("channels/world.yml", false);
        saveResource("channels/staff.yml", false);
        saveResource("rules.yml", false);
        saveResource("highlights.yml", false);
        saveResource("gui/showcase-preview.yml", false);
        saveResource("gui/megaphone.yml", false);
        languageService = new LanguageService(getDataFolder());
        chatConfigFileLoader = new ChatConfigFileLoader(this);
        platformBridge = new PlatformBridge(this);
        dependencyBridge = new DependencyBridge(this);
        channelService = new ChannelService(this::getChatConfig, languageService);
        antiSpamService = new AntiSpamService();
        mentionService = new MentionService(languageService);
        privateMessageService = new PrivateMessageService(this);
        debugService = new DebugService(this);
        filterCloudWordService = new FilterCloudWordService(this);
        textFilterService = new TextFilterService(filterCloudWordService);
        crossServerChatService = new CrossServerChatService(this);
        playerColorPreferenceStore = new PlayerColorPreferenceStore(this);
        playerColorPreferenceRepository = new PlayerColorPreferenceCacheRepository(this, playerColorPreferenceStore, crossServerChatService);
        playerColorService = new PlayerColorService(playerColorPreferenceRepository);
        chatRenderer = new ChatRenderer(
            dependencyBridge,
            (player, config) -> playerColorService.resolve(player, ColorScope.NAME, config.nameColorSettings(), "&f")
        );
        publicChatColorService = new PublicChatColorService();
        publicChatHighlightService = new PublicChatHighlightService();
        showcasePreviewGuiService = new ShowcasePreviewGuiService(this);
        chatShowcaseSnapshotService = new ChatShowcaseSnapshotService(this, showcasePreviewGuiService);
        itemShowcaseService = new ItemShowcaseService(chatShowcaseSnapshotService);
        crossServerLogService = new CrossServerLogService(this);
        megaphoneBalanceStore = new MegaphoneBalanceStore(this, crossServerChatService);
        megaphoneService = new MegaphoneService(this, megaphoneBalanceStore);
        chatMessageProcessor = new ChatMessageProcessor(this);
        reloadChatSettings();
        platformBridge.registerChatListeners();
        registerCommands();
    }

    @Override
    public void onDisable() {
        if (crossServerChatService != null) {
            crossServerChatService.close();
        }
        if (filterCloudWordService != null) {
            filterCloudWordService.close();
        }
    }

    public void reloadChatSettings() {
        reloadConfig();
        languageService.reload(getConfig());
        dependencyBridge.refresh();
        chatConfig = new ChatConfigLoader(languageService).load(chatConfigFileLoader.load(getConfig()));
        if (filterCloudWordService != null) {
            filterCloudWordService.reload(chatConfig.filterSettings().cloudSettings());
        }
        crossServerChatService.reload(chatConfig.crossServerSettings());
        if (megaphoneService != null) {
            megaphoneService.reload(chatConfig.megaphoneSettings(), crossServerChatService.isEnabled());
        }
        if (showcasePreviewGuiService != null) {
            showcasePreviewGuiService.reloadLayout();
        }
        if (playerColorPreferenceRepository != null) {
            playerColorPreferenceRepository.rebind(crossServerChatService.isEnabled());
            Bukkit.getOnlinePlayers().forEach(player -> playerColorPreferenceRepository.preload(player.getUniqueId()));
        }
        if (megaphoneBalanceStore != null) {
            Bukkit.getOnlinePlayers().forEach(player -> megaphoneBalanceStore.preload(player.getUniqueId()));
        }
    }

    public ChatPluginConfig getChatConfig() {
        return chatConfig;
    }

    public PlatformBridge getPlatformBridge() {
        return platformBridge;
    }

    public LanguageService getLanguageService() {
        return languageService;
    }

    public ChannelService getChannelService() {
        return channelService;
    }

    public MentionService getMentionService() {
        return mentionService;
    }

    public PrivateMessageService getPrivateMessageService() {
        return privateMessageService;
    }

    public DebugService getDebugService() {
        return debugService;
    }

    public AntiSpamService getAntiSpamService() {
        return antiSpamService;
    }

    public TextFilterService getTextFilterService() {
        return textFilterService;
    }

    public PlayerColorService getPlayerColorService() {
        return playerColorService;
    }

    public PlayerColorPreferenceCacheRepository getPlayerColorPreferenceRepository() {
        return playerColorPreferenceRepository;
    }

    public PublicChatColorService getPublicChatColorService() {
        return publicChatColorService;
    }

    public PublicChatHighlightService getPublicChatHighlightService() {
        return publicChatHighlightService;
    }

    public ChatRenderer getChatRenderer() {
        return chatRenderer;
    }

    public CrossServerChatService getCrossServerChatService() {
        return crossServerChatService;
    }

    public ShowcasePreviewGuiService getShowcasePreviewGuiService() {
        return showcasePreviewGuiService;
    }

    public ChatShowcaseSnapshotService getChatShowcaseSnapshotService() {
        return chatShowcaseSnapshotService;
    }

    public ItemShowcaseService getItemShowcaseService() {
        return itemShowcaseService;
    }

    public CrossServerLogService getCrossServerLogService() {
        return crossServerLogService;
    }

    public MegaphoneService getMegaphoneService() {
        return megaphoneService;
    }

    public MegaphoneBalanceStore getMegaphoneBalanceStore() {
        return megaphoneBalanceStore;
    }

    public boolean isParticipating(Player player) {
        if (chatConfig.autoJoin()) {
            return !optedOutPlayers.contains(player.getUniqueId());
        }
        return optedInPlayers.contains(player.getUniqueId());
    }

    public void join(Player player) {
        optedOutPlayers.remove(player.getUniqueId());
        optedInPlayers.add(player.getUniqueId());
    }

    public void leave(Player player) {
        optedInPlayers.remove(player.getUniqueId());
        optedOutPlayers.add(player.getUniqueId());
    }

    public void handleCapturedChat(Player sender, String rawMessage) {
        platformBridge.runForPlayer(sender, () -> processChatMessage(sender, rawMessage));
    }

    public void processChatMessage(Player sender, String rawMessage) {
        chatMessageProcessor.process(sender, rawMessage);
    }

    private void registerCommands() {
        YmChatCommand ymChatCommand = new YmChatCommand(this);
        registerCommand("ymchat", ymChatCommand, ymChatCommand);

        ChannelCommand channelCommand = new ChannelCommand(this);
        registerCommand("channel", channelCommand, channelCommand);

        MessageCommand messageCommand = new MessageCommand(this);
        registerCommand("msg", messageCommand, messageCommand);
        registerCommand("m", messageCommand, messageCommand);
        registerCommand("tell", messageCommand, messageCommand);
        registerCommand("w", messageCommand, messageCommand);

        registerCommand("reply", new ReplyCommand(this), null);
        registerCommand("socialspy", new SocialSpyCommand(this), null);
        registerCommand("showitem", new ItemShowcaseCommand(this), null);
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter tabCompleter) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            return;
        }
        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
