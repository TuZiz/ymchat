package ym.ymchat.service.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ym.ymchat.config.chat.SectionStyle;

class ChatRendererTest {

    @Test
    void skipsCrossServerPrefixWhenItContainsLocalServerOrWorldPlaceholders() {
        SectionStyle section = new SectionStyle("~", "&f[&3%multiverse-core_alias%&f]%tags_current% ", null, null, null, null, null);

        assertFalse(ChatRenderer.shouldIncludeCrossServerPrefix(section));
    }

    @Test
    void keepsCrossServerPrefixWhenItOnlyContainsSharedPlayerDecorations() {
        SectionStyle section = new SectionStyle("~", "%tags_current% ", null, null, null, null, null);

        assertTrue(ChatRenderer.shouldIncludeCrossServerPrefix(section));
    }
}
