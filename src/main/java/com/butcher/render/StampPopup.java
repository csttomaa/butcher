package com.butcher.render;

import com.butcher.ButcherClient;
import com.butcher.audio.ButcherAudio;
import com.butcher.gui.GuiDraw;
import com.butcher.ledger.Ledger;
import com.butcher.ledger.Stamp;
import com.butcher.config.ButcherStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Ledger stamp, earned: it comes down onto the screen like a rubber stamp onto paper - big and high, then thump,
 * ink flecks, a shake - with "STAMPED!", its name and its grade under it, and fades after a couple of seconds.
 *
 * Never over a cutscene or the pre-kill hide (it would give a drop away, or sit on top of one): earned stamps wait in
 * a queue and come out one at a time once the screen is the game's again. Drawn by ButcherClient.drawOwnHud.
 */
public final class StampPopup {
    private StampPopup() {}

    // ---- timing, ms from the start
    private static final long FALL = com.butcher.gui.menu.StampCard.FALL, END = com.butcher.gui.menu.StampCard.END;

    /** A pop-up waiting: its stamp, and a grade to show (0: whatever the book says when it comes up). */
    private record Item(Stamp stamp, int grade, boolean test) {}

    private static final ConcurrentLinkedQueue<Item> QUEUE = new ConcurrentLinkedQueue<>();
    private static Stamp current;
    private static int grade;
    private static long startedAt = -1;
    private static boolean thumped;
    private static GuiDraw draw;
    /** Pop-ups shown since the queue was last empty: past {@link #CAP}, the rest are one "+N more" card. */
    private static int burst;
    /** The "+N more" card showing (current is null then), or 0. */
    private static int more;
    public static final int CAP = 3;

    /** A stamp earned or raised a grade (ButcherStats.stamped). Any thread. */
    public static void queue(Stamp s) {
        QUEUE.add(new Item(s, 0, false));
    }

    /** /butcher test ledger: a pop-up at a chosen grade, shown even with pop-ups switched off. */
    public static void queueTest(Stamp s, int grade) {
        QUEUE.add(new Item(s, grade, true));
    }

    /** Whether one is showing or waiting. */
    public static boolean busy() {
        return active() || !QUEUE.isEmpty();
    }

    public static boolean active() {
        return current != null || more > 0;
    }

    /** Takes it off and forgets the queue - the stamps themselves are in the book (/butcher stop, or an error). */
    public static void stop() {
        QUEUE.clear();
        current = null;
        more = 0;
        burst = 0;
    }

    /** Takes the next waiting stamp if the screen is free. Called every frame from drawOwnHud. */
    public static void tick(boolean free) {
        if (active() || !free) return;
        if (QUEUE.isEmpty()) {
            burst = 0;
            return;
        }
        boolean off = ButcherClient.config != null && !ButcherClient.config.ledgerPopups;
        if (!com.butcher.Safety.on("ledger")) {
            QUEUE.clear();
            return;
        }
        if (off) {
            QUEUE.removeIf(it -> !it.test());
            if (QUEUE.isEmpty()) return;
        }
        if (burst >= CAP) {
            int n = 0;
            while (QUEUE.poll() != null) n++;
            more = n;
            burst = 0;
            startedAt = System.currentTimeMillis();
            thumped = true;
            return;
        }
        Item next = QUEUE.poll();
        burst++;
        current = next.stamp();
        grade = next.grade() > 0 ? Math.min(next.grade(), next.stamp().grades())
                : Math.max(1, Ledger.grade(next.stamp(), ButcherStats.get().ledger()));
        startedAt = System.currentTimeMillis();
        thumped = false;
    }

    public static void draw(GuiGraphicsExtractor g) {
        if (!active()) return;
        long t = System.currentTimeMillis() - startedAt;
        if (t >= END) {
            current = null;
            more = 0;
            return;
        }
        if (!thumped && t >= FALL) {
            thumped = true;
            ButcherAudio.onStamp(grade > 1, startedAt);
        }
        if (draw == null) draw = new GuiDraw(Minecraft.getInstance().font);
        draw.g = g;
        try {
            if (current != null) com.butcher.gui.menu.StampCard.paint(draw, g.guiWidth(), g.guiHeight(), t, current, grade);
            else com.butcher.gui.menu.StampCard.paintMore(draw, g.guiWidth(), g.guiHeight(), t, more);
        } finally {
            draw.g = null;
        }
    }

}
