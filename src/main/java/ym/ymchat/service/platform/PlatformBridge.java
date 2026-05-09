package ym.ymchat.service.platform;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.config.chat.TargetMode;
import ym.ymchat.listener.MegaphoneBalanceLifecycleListener;
import ym.ymchat.listener.PlayerColorLifecycleListener;
import ym.ymchat.listener.ShowcasePreviewListener;
import ym.ymchat.listener.SpigotChatListener;

import ym.ymchat.service.text.RichText;
public final class PlatformBridge {

    private final YmChatPlugin plugin;
    private final boolean folia;

    public PlatformBridge(YmChatPlugin plugin) {
        this.plugin = plugin;
        this.folia = hasClass("io.papermc.paper.threadedregions.RegionizedServer");
    }

    public void registerChatListeners() {
        plugin.getServer().getPluginManager().registerEvents(new SpigotChatListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PlayerColorLifecycleListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new MegaphoneBalanceLifecycleListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ShowcasePreviewListener(plugin), plugin);
        plugin.getLogger().info("YmChat registered chat, player data lifecycle, and showcase preview listeners.");
    }

    public void runForPlayer(Player player, Runnable task) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Class<?> consumerClass = Class.forName("java.util.function.Consumer");
            Object consumer = java.lang.reflect.Proxy.newProxyInstance(
                consumerClass.getClassLoader(),
                new Class<?>[]{consumerClass},
                (proxy, method, args) -> {
                    task.run();
                    return null;
                }
            );
            Method runMethod = scheduler.getClass().getMethod("run", org.bukkit.plugin.Plugin.class, consumerClass, Runnable.class);
            runMethod.invoke(scheduler, plugin, consumer, null);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Failed to use Folia entity scheduler, falling back to Bukkit scheduler: " + exception.getMessage());
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runForPlayerLater(Player player, Runnable task, long delayTicks) {
        long safeDelay = Math.max(1L, delayTicks);
        if (!folia) {
            Bukkit.getScheduler().runTaskLater(plugin, task, safeDelay);
            return;
        }

        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Class<?> consumerClass = Class.forName("java.util.function.Consumer");
            Object consumer = java.lang.reflect.Proxy.newProxyInstance(
                consumerClass.getClassLoader(),
                new Class<?>[]{consumerClass},
                (proxy, method, args) -> {
                    task.run();
                    return null;
                }
            );
            Method runDelayedMethod = scheduler.getClass().getMethod(
                "runDelayed",
                org.bukkit.plugin.Plugin.class,
                consumerClass,
                Runnable.class,
                long.class
            );
            runDelayedMethod.invoke(scheduler, plugin, consumer, null, safeDelay);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Failed to use Folia delayed entity scheduler, falling back to Bukkit scheduler: " + exception.getMessage());
            Bukkit.getScheduler().runTaskLater(plugin, task, safeDelay);
        }
    }

    public List<Player> snapshotRecipients(TargetMode targetMode, Player sender) {
        List<Player> recipients = new ArrayList<>(targetMode.selectRecipients(sender));
        recipients.removeIf(player -> player == null || !player.isOnline());
        return recipients;
    }

    public void sendMessage(Player player, Component component) {
        if (plugin.getChatConfig() != null && plugin.getChatConfig().forceLegacy()) {
            player.sendMessage(RichText.serializeToSection(component));
            return;
        }
        if (invokeAdventureSend(player, component)) {
            return;
        }
        if (invokeSpigotComponentSend(player, component)) {
            return;
        }
        player.sendMessage(RichText.serializeToSection(component));
    }

    public void sendConsoleMessage(Component component) {
        CommandSender console = Bukkit.getConsoleSender();
        if (plugin.getChatConfig() != null && plugin.getChatConfig().forceLegacy()) {
            console.sendMessage(RichText.serializeToSection(component));
            return;
        }
        if (!invokeAdventureSend(console, component)) {
            console.sendMessage(RichText.serializeToSection(component));
        }
    }

    public void sendActionbar(Player player, Component component) {
        try {
            player.getClass().getMethod("sendActionBar", Component.class).invoke(player, component);
        } catch (ReflectiveOperationException ignored) {
            player.sendMessage(RichText.serializeToSection(component));
        }
    }

    private boolean invokeAdventureSend(CommandSender sender, Component component) {
        try {
            sender.getClass().getMethod("sendMessage", Component.class).invoke(sender, component);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean invokeSpigotComponentSend(Player player, Component component) {
        try {
            Object spigot = player.getClass().getMethod("spigot").invoke(player);
            Class<?> baseComponentArrayClass = Class.forName("[Lnet.md_5.bungee.api.chat.BaseComponent;");
            Class<?> serializerClass = Class.forName("net.md_5.bungee.chat.ComponentSerializer");
            String json = GsonComponentSerializer.gson().serialize(component);
            Object baseComponents = serializerClass.getMethod("parse", String.class).invoke(null, json);
            spigot.getClass().getMethod("sendMessage", baseComponentArrayClass).invoke(spigot, baseComponents);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
