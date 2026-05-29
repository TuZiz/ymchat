package ym.ymchat.service.crossserver;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import ym.ymchat.config.highlight.PublicChatHighlightSettings;
import ym.ymchat.service.chat.PublicChatHighlightService;
import ym.ymchat.service.color.ColorCodeUtil;
import ym.ymchat.service.color.PublicChatColorService;

final class CrossServerMessageHighlighter {

    private CrossServerMessageHighlighter() {
    }

    static Component apply(
        Component rendered,
        String channelId,
        PublicChatHighlightService highlightService,
        PublicChatHighlightSettings settings
    ) {
        if (rendered == null || highlightService == null || settings == null || !settings.enabled()) {
            return rendered == null ? Component.empty() : rendered;
        }

        List<Component> children = rendered.children();
        if (children.isEmpty()) {
            return highlightSection(rendered, channelId, highlightService, settings);
        }

        Component section = children.getLast();
        Component highlightedSection = highlightSection(section, channelId, highlightService, settings);
        if (highlightedSection == section || highlightedSection.equals(section)) {
            return rendered;
        }

        List<Component> replaced = new ArrayList<>(children);
        replaced.set(replaced.size() - 1, highlightedSection);
        return rendered.children(replaced);
    }

    private static Component highlightSection(
        Component section,
        String channelId,
        PublicChatHighlightService highlightService,
        PublicChatHighlightSettings settings
    ) {
        FlattenResult flattened = flatten(section);
        if (!flattened.supported() || flattened.message().spans().isEmpty()) {
            return section;
        }

        PublicChatColorService.PreparedPublicChatMessage highlighted = highlightService.apply(
            flattened.message(),
            flattened.message().visiblePlainText(),
            channelId,
            settings
        );
        if (highlighted == flattened.message()) {
            return section;
        }
        return highlighted.toComponent();
    }

    private static FlattenResult flatten(Component component) {
        FlattenState initialState = FlattenState.base();
        FlattenAccumulator accumulator = new FlattenAccumulator();
        appendComponent(component, initialState, accumulator);
        if (accumulator.unsupported || accumulator.spans.isEmpty()) {
            return new FlattenResult(false, emptyMessage());
        }
        String visiblePlainText = accumulator.visible.toString();
        return new FlattenResult(
            true,
            new PublicChatColorService.PreparedPublicChatMessage(
                visiblePlainText,
                visiblePlainText,
                "&f",
                false,
                false,
                List.copyOf(accumulator.spans)
            )
        );
    }

    private static PublicChatColorService.PreparedPublicChatMessage emptyMessage() {
        return new PublicChatColorService.PreparedPublicChatMessage(
            "",
            "",
            "&f",
            false,
            false,
            List.of()
        );
    }

    private static void appendComponent(Component component, FlattenState inherited, FlattenAccumulator accumulator) {
        if (component == null || accumulator.unsupported) {
            return;
        }

        FlattenState state = inherited.merge(component);
        if (state.unsupported()) {
            accumulator.unsupported = true;
            return;
        }

        if (component instanceof TextComponent textComponent) {
            appendText(textComponent.content(), state, accumulator);
            for (Component child : component.children()) {
                appendComponent(child, state, accumulator);
            }
            return;
        }

        if (!component.children().isEmpty()) {
            accumulator.unsupported = true;
        }
    }

    private static void appendText(String text, FlattenState state, FlattenAccumulator accumulator) {
        if (text == null || text.isEmpty()) {
            return;
        }
        accumulator.visible.append(text);
        accumulator.spans.add(new PublicChatColorService.TextSpan(
            text,
            state.colorValue(),
            state.formatCodes(),
            state.hover(),
            state.click()
        ));
    }

    private static final class FlattenAccumulator {

        private final StringBuilder visible = new StringBuilder();
        private final List<PublicChatColorService.TextSpan> spans = new ArrayList<>();
        private boolean unsupported;
    }

    private record FlattenResult(boolean supported, PublicChatColorService.PreparedPublicChatMessage message) {
    }

    private record FlattenState(
        String colorValue,
        boolean obfuscated,
        boolean bold,
        boolean strikethrough,
        boolean underlined,
        boolean italic,
        Component hover,
        ClickEvent click,
        boolean unsupported
    ) {

        private static FlattenState base() {
            return new FlattenState("&f", false, false, false, false, false, null, null, false);
        }

        private FlattenState merge(Component component) {
            String mergedColor = colorValue;
            TextColor color = component.color();
            if (color != null) {
                String normalized = ColorCodeUtil.normalizeBaseColorValue(color.asHexString());
                if (normalized != null) {
                    mergedColor = normalized;
                }
            }

            HoverEvent<?> hoverEvent = component.hoverEvent();
            Component mergedHover = hover;
            boolean unsupportedHover = unsupported;
            if (hoverEvent != null) {
                if (hoverEvent.action() == HoverEvent.Action.SHOW_TEXT && hoverEvent.value() instanceof Component componentHover) {
                    mergedHover = componentHover;
                } else {
                    unsupportedHover = true;
                }
            }

            ClickEvent mergedClick = component.clickEvent() == null ? click : component.clickEvent();
            return new FlattenState(
                mergedColor,
                mergeDecoration(component, TextDecoration.OBFUSCATED, obfuscated),
                mergeDecoration(component, TextDecoration.BOLD, bold),
                mergeDecoration(component, TextDecoration.STRIKETHROUGH, strikethrough),
                mergeDecoration(component, TextDecoration.UNDERLINED, underlined),
                mergeDecoration(component, TextDecoration.ITALIC, italic),
                mergedHover,
                mergedClick,
                unsupportedHover
            );
        }

        private String formatCodes() {
            StringBuilder builder = new StringBuilder();
            if (obfuscated) {
                builder.append("&k");
            }
            if (bold) {
                builder.append("&l");
            }
            if (strikethrough) {
                builder.append("&m");
            }
            if (underlined) {
                builder.append("&n");
            }
            if (italic) {
                builder.append("&o");
            }
            return builder.toString();
        }

        private static boolean mergeDecoration(Component component, TextDecoration decoration, boolean inherited) {
            return switch (component.decoration(decoration)) {
                case TRUE -> true;
                case FALSE -> false;
                case NOT_SET -> inherited;
            };
        }
    }
}
