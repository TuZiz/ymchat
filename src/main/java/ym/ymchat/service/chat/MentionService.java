package ym.ymchat.service.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ym.ymchat.config.chat.MentionSettings;

import ym.ymchat.service.color.ColorCodeUtil;
import ym.ymchat.service.color.PublicChatColorService;
import ym.ymchat.service.language.LanguageService;
import ym.ymchat.service.platform.PlatformBridge;
import ym.ymchat.service.text.RichText;
public final class MentionService {

    private static final Pattern PLAIN_NAME_PATTERN = Pattern.compile("(?<![@A-Za-z0-9_])([A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])");

    private final LanguageService languageService;

    public MentionService() {
        this(null);
    }

    public MentionService(LanguageService languageService) {
        this.languageService = languageService;
    }

    public MentionResult extractMentions(Player sender, String visiblePlainText, MentionSettings settings) {
        if (!settings.enabled()) {
            return MentionResult.none();
        }

        return extractMentions(
            visiblePlainText,
            settings,
            name -> Bukkit.getOnlinePlayers().stream().anyMatch(player -> player.getName().equalsIgnoreCase(name)),
            sender.hasPermission(settings.everyonePermission())
        );
    }

    public MentionResult extractMentions(
        String visiblePlainText,
        MentionSettings settings,
        Predicate<String> onlinePlayerLookup,
        boolean everyoneAllowed
    ) {
        if (!settings.enabled()) {
            return MentionResult.none();
        }

        String input = visiblePlainText == null ? "" : visiblePlainText;
        Pattern pattern = Pattern.compile(Pattern.quote(settings.prefix()) + "([A-Za-z0-9_]{1,16})");
        Matcher matcher = pattern.matcher(input);
        List<MentionHit> hits = new ArrayList<>();

        while (matcher.find()) {
            String token = matcher.group(1);
            if (settings.allowEveryone()
                && token.equalsIgnoreCase(settings.everyoneToken())
                && everyoneAllowed) {
                hits.add(new MentionHit(token, matcher.start(), matcher.end(), true));
            } else if (onlinePlayerLookup.test(token)) {
                hits.add(new MentionHit(token, matcher.start(), matcher.end(), false));
            }
        }

        if (settings.matchPlainNames()) {
            Matcher plainNameMatcher = PLAIN_NAME_PATTERN.matcher(input);
            while (plainNameMatcher.find()) {
                String token = plainNameMatcher.group(1);
                if (prefixedAt(input, plainNameMatcher.start(1), settings.prefix())) {
                    continue;
                }
                if (onlinePlayerLookup.test(token)) {
                    hits.add(new MentionHit(token, plainNameMatcher.start(1), plainNameMatcher.end(1), false));
                }
            }
        }

        hits.sort(Comparator.comparingInt(MentionHit::start).thenComparingInt(MentionHit::end));
        Set<String> names = new LinkedHashSet<>();
        boolean everyoneMentioned = false;
        List<MentionRange> highlightRanges = new ArrayList<>();
        int lastEnd = -1;
        for (MentionHit hit : hits) {
            if (hit.start() < lastEnd) {
                continue;
            }
            if (hit.everyone()) {
                everyoneMentioned = true;
            } else {
                names.add(hit.name());
            }
            highlightRanges.add(new MentionRange(hit.start(), hit.end()));
            lastEnd = hit.end();
        }

        return new MentionResult(List.copyOf(names), everyoneMentioned, List.copyOf(highlightRanges));
    }

    private boolean prefixedAt(String input, int tokenStart, String prefix) {
        if (input == null || prefix == null || prefix.isBlank() || tokenStart < prefix.length()) {
            return false;
        }
        return input.regionMatches(tokenStart - prefix.length(), prefix, 0, prefix.length());
    }

    public List<Player> resolveMentionedRecipients(Player sender, List<Player> recipients, MentionResult result) {
        if (result.everyoneMentioned()) {
            return recipients.stream().filter(player -> !player.getUniqueId().equals(sender.getUniqueId())).toList();
        }
        Set<String> lowered = result.mentionedNames().stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        return recipients.stream()
            .filter(player -> lowered.contains(player.getName().toLowerCase(Locale.ROOT)))
            .toList();
    }

    public void notifyMentions(Player sender, List<Player> recipients, MentionResult result, PlatformBridge platformBridge, MentionSettings settings) {
        notifyMentionTargets(sender.getName(), resolveMentionedRecipients(sender, recipients, result), platformBridge, settings);
    }

    public void notifyRemoteMentions(String senderName, List<Player> targets, PlatformBridge platformBridge, MentionSettings settings) {
        notifyMentionTargets(senderName, targets, platformBridge, settings);
    }

    public Component applyHighlights(PublicChatColorService.PreparedPublicChatMessage message, MentionSettings settings, MentionResult result) {
        if (!settings.enabled() || result.highlightRanges().isEmpty()) {
            return message.toComponent();
        }

        TextColor highlightColor = RichText.styleOf(settings.highlightColor()).color();
        if (highlightColor == null) {
            return message.toComponent();
        }

        TextComponent.Builder builder = Component.text();
        int globalIndex = 0;
        int rangeIndex = 0;
        List<MentionRange> ranges = result.highlightRanges();

        for (PublicChatColorService.TextSpan span : message.spans()) {
            if (span.text() == null || span.text().isEmpty()) {
                continue;
            }

            int localIndex = 0;
            while (localIndex < span.text().length()) {
                while (rangeIndex < ranges.size() && ranges.get(rangeIndex).end() <= globalIndex + localIndex) {
                    rangeIndex++;
                }
                MentionRange range = rangeIndex < ranges.size() ? ranges.get(rangeIndex) : null;
                int absoluteIndex = globalIndex + localIndex;
                if (range == null) {
                    appendSegment(builder, span, localIndex, span.text().length(), null);
                    localIndex = span.text().length();
                    continue;
                }

                if (range.start() > absoluteIndex) {
                    int plainEnd = Math.min(span.text().length(), localIndex + (range.start() - absoluteIndex));
                    appendSegment(builder, span, localIndex, plainEnd, null);
                    localIndex = plainEnd;
                    continue;
                }

                int highlightEnd = Math.min(span.text().length(), localIndex + (range.end() - absoluteIndex));
                appendSegment(builder, span, localIndex, highlightEnd, highlightColor);
                localIndex = highlightEnd;
                if (globalIndex + localIndex >= range.end()) {
                    rangeIndex++;
                }
            }
            globalIndex += span.text().length();
        }

        return builder.build();
    }

    public void notifyMentionTargets(String senderName, List<Player> targets, PlatformBridge platformBridge, MentionSettings settings) {
        for (Player target : targets) {
            platformBridge.runForPlayer(target, () -> {
                Sound sound = parseSound(settings.sound());
                if (sound != null) {
                    target.playSound(target.getLocation(), sound, 1F, 1F);
                }
                if (settings.notifyActionbar()) {
                    String actionbar = languageService == null
                        ? "&#FFFF55You were mentioned by " + senderName
                        : languageService.get("mentions.actionbar", "sender", senderName);
                    platformBridge.sendActionbar(target, RichText.deserialize(actionbar));
                }
            });
        }
    }

    private Sound parseSound(String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void appendSegment(
        TextComponent.Builder builder,
        PublicChatColorService.TextSpan span,
        int start,
        int end,
        TextColor highlightColor
    ) {
        if (end <= start) {
            return;
        }
        String text = span.text().substring(start, end);
        if (highlightColor == null) {
            builder.append(span.slice(start, end).toComponent());
            return;
        }
        String stylePrefix = highlightColor == null
            ? span.stylePrefix()
            : highlightPrefix(span, highlightColor.asHexString());
        builder.append(Component.text(text).style(RichText.styleOf(stylePrefix)));
    }

    private String highlightPrefix(PublicChatColorService.TextSpan span, String color) {
        String normalized = ColorCodeUtil.normalizeBaseColorValue(color);
        return (normalized == null ? span.colorValue() : normalized) + span.formatCodes();
    }

    public record MentionRange(int start, int end) {
    }

    private record MentionHit(String name, int start, int end, boolean everyone) {
    }

    public record MentionResult(List<String> mentionedNames, boolean everyoneMentioned, List<MentionRange> highlightRanges) {

        public static MentionResult none() {
            return new MentionResult(List.of(), false, List.of());
        }

        public String describe() {
            if (everyoneMentioned) {
                return "@all";
            }
            return mentionedNames.isEmpty() ? "none" : String.join(",", mentionedNames);
        }
    }
}
