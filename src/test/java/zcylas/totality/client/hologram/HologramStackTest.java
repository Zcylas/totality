package zcylas.totality.client.hologram;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Notification V2 hologram lifecycle with a manual clock: identity/keys, queueing, priority
 * interruption with suspend/resume, in-place updates, dismissal, lifetime and cleanup. No rendering,
 * no running game.
 */
class HologramStackTest {

    private long nanos;
    private final List<String> events = new ArrayList<>();
    private final HologramStack stack = new HologramStack(() -> nanos, new HologramStack.Listener() {
        @Override
        public void opened(HologramStack.Entry entry, boolean resumed) {
            events.add((resumed ? "resumed " : "opened ") + entry.spec().id());
        }

        @Override
        public void updated(HologramStack.Entry entry) {
            events.add("updated " + entry.spec().id());
        }

        @Override
        public void closing(HologramStack.Entry entry, HologramStack.CloseReason reason) {
            events.add("closing " + entry.spec().id() + " " + reason);
        }

        @Override
        public void dropped(HologramStack.Entry entry) {
            events.add("dropped " + entry.spec().id());
        }
    });

    private static HologramSpec spec(String id, HologramPriority priority, int lifetime) {
        return HologramSpec.builder(id).priority(priority).lifetimeTicks(lifetime).silent().build();
    }

    private static HologramSpec keyed(String id, String key, HologramPriority priority, int lifetime) {
        return HologramSpec.builder(id).aggregationKey(key).priority(priority).lifetimeTicks(lifetime).silent().build();
    }

    /** Lets the opening animation finish. */
    private void open() {
        nanos += HologramStack.OPEN_NANOS;
        stack.tick(true);
    }

    /** Lets a closing animation finish. */
    private void finishClose() {
        nanos += HologramStack.CLOSE_NANOS;
        stack.tick(true);
    }

    private String activeId() {
        return stack.active() == null ? null : stack.active().spec().id();
    }

    @Test
    void firstHologramShowsAndOpens() {
        assertEquals(HologramStack.ShowResult.SHOWN, stack.show(spec("a", HologramPriority.NORMAL, 10)));
        assertEquals(HologramStack.Phase.OPENING, stack.active().phase());
        open();
        assertEquals(HologramStack.Phase.SHOWN, stack.active().phase());
    }

    @Test
    void equalPriorityQueuesInArrivalOrder() {
        stack.show(spec("a", HologramPriority.NORMAL, 0));
        assertEquals(HologramStack.ShowResult.QUEUED, stack.show(spec("b", HologramPriority.NORMAL, 0)));
        assertEquals(HologramStack.ShowResult.QUEUED, stack.show(spec("c", HologramPriority.NORMAL, 0)));
        open();
        stack.dismissActive(HologramStack.CloseReason.DISMISSED);
        finishClose();
        assertEquals("b", activeId());
        open();
        stack.dismissActive(HologramStack.CloseReason.DISMISSED);
        finishClose();
        assertEquals("c", activeId());
    }

    @Test
    void higherPriorityWaitsAheadOfLowerInTheQueue() {
        stack.show(spec("a", HologramPriority.HIGH, 0));
        stack.show(spec("low", HologramPriority.LOW, 0));
        stack.show(spec("normal", HologramPriority.NORMAL, 0));
        open();
        stack.dismissActive(HologramStack.CloseReason.DISMISSED);
        finishClose();
        assertEquals("normal", activeId());
    }

    @Test
    void higherPriorityInterruptsAndTheInterruptedOneResumesWithItsRemainingTime() {
        stack.show(spec("quest", HologramPriority.NORMAL, 100));
        open();
        for (int i = 0; i < 30; i++) stack.tick(true);
        assertEquals(70, stack.active().ticksRemaining());

        assertEquals(HologramStack.ShowResult.INTERRUPTED, stack.show(spec("urgent", HologramPriority.CRITICAL, 0)));
        assertEquals("urgent", activeId());
        assertEquals(1, stack.suspendedCount());
        assertEquals("quest", stack.suspendedTop().spec().id());
        open();
        for (int i = 0; i < 50; i++) stack.tick(true);
        assertEquals(70, stack.suspendedTop().ticksRemaining(), "a suspended hologram's time does not run");

        stack.dismissActive(HologramStack.CloseReason.ACTION);
        finishClose();
        assertEquals("quest", activeId());
        assertTrue(stack.active().resumed());
        assertEquals(70, stack.active().ticksRemaining(), "resumes with the time it had left");
        assertTrue(events.contains("resumed quest"));
    }

    @Test
    void equalPriorityNeverInterrupts() {
        stack.show(spec("a", HologramPriority.HIGH, 0));
        assertEquals(HologramStack.ShowResult.QUEUED, stack.show(spec("b", HologramPriority.HIGH, 0)));
        assertEquals("a", activeId());
        assertEquals(0, stack.suspendedCount());
    }

    @Test
    void suspendedReturnsBeforeAnEqualPriorityQueuedOne() {
        stack.show(spec("first", HologramPriority.NORMAL, 0));
        stack.show(spec("interrupter", HologramPriority.HIGH, 0));
        stack.show(spec("later", HologramPriority.NORMAL, 0));
        open();
        stack.dismissActive(HologramStack.CloseReason.DISMISSED);
        finishClose();
        assertEquals("first", activeId(), "the interrupted panel returns first on equal priority");
    }

    @Test
    void aHigherPriorityQueuedOneBeatsTheSuspendedOne() {
        stack.show(spec("low", HologramPriority.LOW, 0));
        stack.show(spec("critical", HologramPriority.CRITICAL, 0));   // interrupts low
        stack.show(spec("high", HologramPriority.HIGH, 0));           // queued behind critical
        open();
        stack.dismissActive(HologramStack.CloseReason.DISMISSED);
        finishClose();
        assertEquals("high", activeId());
        assertEquals(1, stack.suspendedCount());
    }

    @Test
    void sameKeyUpdatesTheDisplayedHologramInPlace() {
        stack.show(keyed("voice_loading", "voice", HologramPriority.LOW, 100));
        open();
        HologramStack.Entry entry = stack.active();
        for (int i = 0; i < 40; i++) stack.tick(true);
        assertEquals(HologramStack.ShowResult.UPDATED, stack.show(keyed("voice_ready", "voice", HologramPriority.NORMAL, 200)));
        assertSame(entry, stack.active(), "same panel, new content");
        assertEquals("voice_ready", activeId());
        assertEquals(200, entry.ticksRemaining(), "lifetime restarts with the new content");
        assertEquals(HologramStack.Phase.SHOWN, entry.phase());
        assertEquals(0, stack.waitingCount());
    }

    @Test
    void sameIdWithoutKeyNeverStacksDuplicates() {
        stack.show(spec("a", HologramPriority.NORMAL, 0));
        stack.show(spec("b", HologramPriority.NORMAL, 0));
        stack.show(spec("b", HologramPriority.NORMAL, 0));
        stack.show(spec("a", HologramPriority.NORMAL, 0));
        assertEquals(1, stack.waitingCount());
        assertEquals(HologramStack.ShowResult.UPDATED, stack.show(spec("a", HologramPriority.NORMAL, 0)));
    }

    @Test
    void updatingAWaitingHologramReplacesItAndReevaluatesPriority() {
        stack.show(spec("quest", HologramPriority.NORMAL, 0));
        stack.show(keyed("loading", "voice", HologramPriority.LOW, 0));
        assertEquals(1, stack.waitingCount());
        assertEquals(HologramStack.ShowResult.INTERRUPTED, stack.show(keyed("failed", "voice", HologramPriority.HIGH, 0)));
        assertEquals("failed", activeId());
        assertEquals(0, stack.waitingCount(), "the stale 'loading' is gone");
    }

    @Test
    void lifetimeCountsOnlyWhileShownAndVisibleAndNotAimedAt() {
        stack.show(spec("a", HologramPriority.NORMAL, 5));
        stack.tick(true);
        assertEquals(5, stack.active().ticksRemaining(), "not while opening");
        open();
        stack.tick(false);
        assertEquals(5, stack.active().ticksRemaining(), "not while hidden (screen open, F1, paused)");
        stack.active().aimedAt = true;
        stack.tick(true);
        assertEquals(5, stack.active().ticksRemaining(), "not while the player is looking at it");
        stack.active().aimedAt = false;
        for (int i = 0; i < 4; i++) stack.tick(true);
        assertEquals(HologramStack.Phase.SHOWN, stack.active().phase());
        stack.tick(true);
        assertEquals(HologramStack.Phase.CLOSING, stack.active().phase());
        assertEquals(HologramStack.CloseReason.EXPIRED, stack.active().closeReason());
        finishClose();
        assertNull(stack.active());
        assertTrue(stack.isEmpty());
    }

    @Test
    void zeroLifetimeStaysUntilDismissed() {
        stack.show(spec("a", HologramPriority.NORMAL, 0));
        open();
        for (int i = 0; i < 10_000; i++) stack.tick(true);
        assertEquals(HologramStack.Phase.SHOWN, stack.active().phase());
        assertTrue(stack.dismiss("a", HologramStack.CloseReason.DISMISSED));
        assertEquals(HologramStack.Phase.CLOSING, stack.active().phase());
    }

    @Test
    void closingStaysOnScreenForTheCloseAnimationThenPromotesTheNext() {
        stack.show(spec("a", HologramPriority.NORMAL, 0));
        stack.show(spec("b", HologramPriority.NORMAL, 0));
        open();
        stack.dismissActive(HologramStack.CloseReason.DISMISSED);
        nanos += HologramStack.CLOSE_NANOS - 1;
        stack.tick(true);
        assertEquals("a", activeId());
        nanos += 1;
        stack.tick(true);
        assertEquals("b", activeId());
    }

    @Test
    void anUpdateRevivesAClosingHologram() {
        stack.show(keyed("a", "k", HologramPriority.NORMAL, 0));
        open();
        stack.dismissActive(HologramStack.CloseReason.DISMISSED);
        assertEquals(HologramStack.ShowResult.UPDATED, stack.show(keyed("a2", "k", HologramPriority.NORMAL, 0)));
        assertEquals(HologramStack.Phase.OPENING, stack.active().phase());
        assertNull(stack.active().closeReason());
    }

    @Test
    void dismissingAWaitingOrSuspendedHologramDropsIt() {
        stack.show(spec("low", HologramPriority.LOW, 0));
        stack.show(spec("high", HologramPriority.HIGH, 0));
        stack.show(spec("queued", HologramPriority.NORMAL, 0));
        assertTrue(stack.dismiss("low", HologramStack.CloseReason.CANCELLED));
        assertTrue(stack.dismiss("queued", HologramStack.CloseReason.CANCELLED));
        assertEquals(0, stack.suspendedCount());
        assertEquals(0, stack.waitingCount());
        assertFalse(stack.dismiss("missing", HologramStack.CloseReason.CANCELLED));
    }

    @Test
    void clearDropsEverythingImmediately() {
        stack.show(spec("a", HologramPriority.LOW, 0));
        stack.show(spec("b", HologramPriority.HIGH, 0));
        stack.show(spec("c", HologramPriority.NORMAL, 0));
        stack.clear();
        assertTrue(stack.isEmpty());
        assertNull(stack.active());
        stack.tick(true); // nothing to do, nothing thrown
        assertEquals(3, events.stream().filter(e -> e.startsWith("dropped")).count());
    }

    @Test
    void theWaitingQueueIsBounded() {
        stack.show(spec("active", HologramPriority.CRITICAL, 0));
        for (int i = 0; i < HologramStack.MAX_WAITING + 5; i++) stack.show(spec("w" + i, HologramPriority.NORMAL, 0));
        assertEquals(HologramStack.MAX_WAITING, stack.waitingCount());
    }

    @Test
    void voiceOffersOnlyOptedInHologramsAndOnlyTheIntentsOfTheirOwnActions() {
        var confirm = new HologramAction("confirm", net.minecraft.network.chat.Component.literal("Confirm"), true);
        var cancel = new HologramAction("cancel", net.minecraft.network.chat.Component.literal("Cancel"), false);
        var later = new HologramAction("later", net.minecraft.network.chat.Component.literal("Later"), false);
        assertTrue(HologramVoice.commands(HologramSpec.builder("a").action(confirm).build()).isEmpty(),
                "a hologram that did not opt in never listens");
        assertEquals(java.util.Map.of("confirm", "confirm"),
                HologramVoice.commands(HologramSpec.builder("b").action(confirm).action(later).voiceCommands().build()),
                "only intents of actions the hologram actually has");
        assertEquals(java.util.List.of("confirm", "cancel"), java.util.List.copyOf(HologramVoice.commands(
                HologramSpec.builder("c").action(confirm).action(cancel).voiceCommands().build()).keySet()));
        assertEquals(java.util.Map.of("dismiss", HologramAction.DISMISS),
                HologramVoice.commands(HologramSpec.builder("d").dismissButton().voiceCommands().build()),
                "a real Dismiss action offers the Dismiss intent");
        assertFalse(HologramVoice.commands(HologramSpec.builder("e").action(confirm).action(cancel).voiceCommands().build())
                .containsKey("dismiss"), "a decision prompt without Dismiss never pretends to minimize");
        assertTrue(HologramVoice.SPOKEN.get("confirm").contains("confirmed"));
        assertFalse(HologramVoice.SPOKEN.get("cancel").stream().anyMatch(HologramVoice.SPOKEN.get("dismiss")::contains),
                "Dismiss and Cancel never share a phrase");
    }

    @Test
    void styleSuppliesDefaultIconAndSoundUnlessSilenced() {
        HologramSpec warn = HologramSpec.builder("w").style(HologramStyle.WARNING).build();
        assertEquals(HologramIcon.WARNING, warn.icon());
        assertEquals(HologramSound.WARNING, warn.openSound());
        assertNull(HologramSpec.builder("s").silent().build().openSound());
        assertEquals("k", HologramSpec.builder("id").aggregationKey("k").build().key());
        assertEquals("id", HologramSpec.builder("id").build().key());
    }
}
