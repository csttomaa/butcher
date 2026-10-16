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

    private static final ConcurrentLinkedQueue<Stamp> QUEUE = new ConcurrentLinkedQueue<>();
    private static Stamp current;
    private static int grade;
    private static long startedAt = -1;
    private static boolean thumped;
    private static GuiDraw draw;

    /** A stamp earned or raised a grade (ButcherStats.stamped). Any thread. */
    public static void queue(Stamp s) {
        QUEUE.add(s);
    }

    /** Whether one is showing or waiting. */
    public static boolean busy() {
        return current != null || !QUEUE.isEmpty();
    }

    public static boolean active() {
        return current != null;
    }

    /** Takes the next waiting stamp if the screen is free. Called every frame from drawOwnHud. */
    public static void tick(boolean free) {
        if (current != null || !free) return;
        Stamp next = QUEUE.poll();
        if (next == null) return;
        if (ButcherClient.config != null && !ButcherClient.config.ledgerPopups) {
            QUEUE.clear();
            return;
        }
        current = next;
        grade = Ledger.grade(next, ButcherStats.get().ledger());
        startedAt = System.currentTimeMillis();
        thumped = false;
    }

    public static void draw(GuiGraphicsExtractor g) {
        if (current == null) return;
        long t = System.currentTimeMillis() - startedAt;
        if (t >= END) {
            current = null;
            return;
        }
        if (!thumped && t >= FALL) {
            thumped = true;
            ButcherAudio.onStamp(grade > 1, startedAt);
        }
        if (draw == null) draw = new GuiDraw(Minecraft.getInstance().font);
        draw.g = g;
        try {
            com.butcher.gui.menu.StampCard.paint(draw, g.guiWidth(), g.guiHeight(), t, current, grade);
        } finally {
            draw.g = null;
        }
    }

}
