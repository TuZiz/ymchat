package ym.ymchat.service.megaphone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BossBarTextAnimatorTest {

    @Test
    void typingKeepsLegacyColorCodesIntact() {
        assertEquals("\u00A7aH", BossBarTextAnimator.frame("\u00A7aHi", "typing", 1, 4));
        assertEquals("\u00A7aHi", BossBarTextAnimator.frame("\u00A7aHi", "typing", 2, 4));
    }

    @Test
    void flowMovesTextToTheRight() {
        assertEquals("  \u00A7aHi", BossBarTextAnimator.frame("\u00A7aHi", "flow", 3, 4));
    }

    @Test
    void typingFlowTypesThenStartsFlowing() {
        assertEquals("\u00A7aH", BossBarTextAnimator.frame("\u00A7aHi", "typing-flow", 1, 4));
        assertEquals(" \u00A7aHi", BossBarTextAnimator.frame("\u00A7aHi", "typing-flow", 4, 4));
    }
}
