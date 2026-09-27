package com.butcher.detect.vanguard;

import com.butcher.detect.DecisionLog;
import com.butcher.detect.vanguard.VanguardLoot.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notices a Vanguard Frozen Corpse being opened, and reads what came out of it.
 *
 * <h2>Why this is so much simpler than the Scatha</h2>
 *
 * Hypixel says nothing at all when a Scatha dies, which is why {@code detect/} has a worm
 * tracker, an inventory differ, a claim window and a whole inference path for gemstones. A
 * corpse announces itself: a header, one line per drop, and a separator to close. The
 * message is <b>complete before anything is drawn</b>, so there is no window to hold open,
 * no cache to claim from, and no outcome to fill in later.
 *
 * It is also better than the inventory diff would be. Items that go straight into a sack
 * never touch the inventory - the exact problem that forced gemstones to be inferred on
 * the Scatha - but they are still printed here.
 *
 * <h2>The state machine, and the three ways out of it</h2>
 *
 * Header opens it, item lines accumulate, separator closes it. SkyOcean's version has only
 * that third exit, and its {@code parsing} flag is a static boolean that nothing else ever
 * clears - so if the separator never arrives, because another mod filtered it or the
 * message was truncated, it stays open forever and every later chat line is fed to an item
 * pattern loose enough to match most of them. This one also gives up after
 * {@link #MAX_LINES} lines and after {@link #TIMEOUT_TICKS} ticks.
 *
 * Every one of those exits is narrated. A parser that gives up silently is indistinguishable
 * from a parser that is broken, which is the whole reason {@link DecisionLog} exists.
 *
 * <h2>Sampling the other corpse types</h2>
 *
 * Only Vanguard is animated. But the header regex matches every type, and a Lapis, Umber or
 * Tungsten message is copied into the log verbatim and otherwise ignored - chat keeps it,
 * no haul is emitted, no stats move. A Lapis corpse costs no key, which makes it the only
 * way to confirm the format without spending thirty-five million coins on a Skeleton Key.
 */
public final class VanguardWatcher {

    /**
     * The header of any corpse's loot message, with the type captured.
     *
     * Deliberately not pinned to VANGUARD. Only a Vanguard corpse is animated, but a Lapis
     * one needs no key at all, and matching every type is what lets a free corpse confirm
     * the message format - see the sampling branch in {@link #onChatLine}.
     *
     * Leading whitespace because Hypixel centres it; trailing because some versions of the
     * message carry a space after the bang and some do not.
     */
    private static final Pattern START = Pattern.compile("^\\s*(?<type>[A-Z]+) CORPSE LOOT!\\s*$");

    /**
     * One drop. The name is reluctant so the {@code xN} suffix is not swallowed into it,
     * and the count is optional because a single item is printed without one.
     */
    private static final Pattern ITEM =
            Pattern.compile("^\\s+(?<item>.+?)(?:\\s+x(?<amount>[\\d,]+))?\\s*$");

    /** The rule under the message. Loose on length; it is only ever tested while open. */
    private static final Pattern END = Pattern.compile("^\\s*[\\u25AC]{10,}\\s*$");

    /** The " (2)" a chat-compacting mod leaves on a folded line. */
    private static final Pattern COLLAPSED = Pattern.compile("\\s*\\(\\d+\\)\\s*$");

    /** More lines than any corpse can produce - 8 rolls, plus room for the message's own furniture. */
    private static final int MAX_LINES = 20;

    /** The whole message arrives in one tick. Two seconds is already absurdly generous. */
    private static final int TIMEOUT_TICKS = 40;

    private final DecisionLog log;
    private final Consumer<CorpseHaul> onHaul;

    private boolean parsing;
    /** Non-null while copying a non-Vanguard corpse's message into the log for reference. */
    private String sampling;
    private int sampleLines;
    private int lines;
    /**
     * Where the loot starts, once a heading has been seen.
     *
     * Zero until then, which is also the fallback: a message with no heading at all keeps
     * everything, rather than silently producing an empty haul and no animation.
     */
    private int afterHeading;
    private int ticksOpen;
    private final List<CorpseHaul.Drop> collected = new ArrayList<>();

    public VanguardWatcher(DecisionLog log, Consumer<CorpseHaul> onHaul) {
        this.log = log;
        this.onHaul = onHaul;
    }

    /** True while a loot message is being read - used to keep those lines out of chat. */
    public boolean parsing() { return parsing; }

    /**
     * Offer one chat line.
     *
     * @return true if the line belongs to a loot message and should be hidden from chat
     */
    public boolean onChatLine(String raw) {
        String text = decollapse(DecisionLog.strip(raw));

        Matcher header = START.matcher(text);
        if (header.matches()) {
            String type = header.group("type");
            if (!"VANGUARD".equals(type)) {
                // A Lapis, Umber or Tungsten corpse. We do not animate those - but the
                // message is the same shape, and a Lapis corpse costs no key at all,
                // which makes it the only way to confirm the format without spending
                // thirty-five million coins on a Skeleton Key. So the lines get copied
                // into the decision log verbatim and nothing else happens: chat keeps
                // them, no haul is emitted, no stats move.
                sampling = type;
                sampleLines = 0;
                log.note("§bSampling a " + type + " corpse message §8- format reference only, "
                        + "nothing will be animated");
                return false;
            }
            if (parsing) {
                log.note("§eA second Vanguard header arrived before the first closed §8- "
                        + "dropping " + collected.size() + " line(s) and starting again");
            }
            open();
            return true;
        }

        if (sampling != null) {
            if (END.matcher(text).matches() || ++sampleLines > MAX_LINES) {
                log.note("§b  [" + sampling + "] (end of message)");
                sampling = null;
            } else {
                log.note("§b  [" + sampling + "] \"" + text + "\"");
            }
            return false;
        }

        if (!parsing) return false;

        ticksOpen = 0;

        if (END.matcher(text).matches()) {
            finish("the message closed normally");
            return true;
        }

        if (++lines > MAX_LINES) {
            abandon("more than " + MAX_LINES + " lines arrived without a closing rule");
            return false;
        }

        // The blank line the message pads itself with, directly under the header. It has
        // no leading whitespace at all, so ITEM does not match it - and abandoning here
        // killed every parse on its second line, before a single item had been read.
        if (text.isBlank()) return true;

        // "REWARDS", the sub-heading between the header and the items. ITEM matches it
        // quite happily and it used to be collected as a drop called REWARDS, inflating
        // every corpse's roll count by one. Headings are the only lines with no lower
        // case in them; every item name has some.
        if (isHeading(text)) {
            // Everything collected so far was preamble, not loot. Hypixel puts notices
            // above the REWARDS heading - "+1 bonus drop!" when the Corpse Milestone perk
            // procs - and they are indented exactly like items, so they parse as drops
            // called "+1 bonus drop!" and inflate the roll count.
            //
            // Cutting at the heading rather than matching that one string means the next
            // notice Hypixel adds is handled too, without another release.
            afterHeading = collected.size();
            return true;
        }

        Matcher item = ITEM.matcher(text);
        if (!item.matches()) {
            // Another mod writing into the middle of the message - Skyblocker puts a
            // "Corpse Profit" line in, flush against the margin, right before the closing
            // rule. This used to abandon the parse and throw the whole haul away.
            //
            // Skipped rather than fatal, and not hidden either: it is not our line. The
            // runaway that abandoning was there to prevent is already covered by the line
            // cap, the tick timeout and the closing rule.
            log.note("§8Ignoring a line inside the loot message: \"" + trim(text) + "\"");
            return false;
        }

        String name = item.group("item").trim();
        if (name.isEmpty()) return true;

        int amount = 1;
        String count = item.group("amount");
        if (count != null) {
            try {
                amount = Math.max(1, Integer.parseInt(count.replace(",", "")));
            } catch (NumberFormatException e) {
                log.note("§cCould not read the count \"" + count + "\" on \"" + name + "\" §8- treating it as 1");
            }
        }

        Entry entry = VanguardLoot.find(name);
        if (entry == null) {
            // Not a failure - a lesson. The table is checked against the NEU item
            // repository, so a miss here means Hypixel renamed something or prints it
            // differently in chat than in an item tooltip.
            log.note("§6Unknown Vanguard drop \"" + name + "\" §8- counting it as a common. "
                    + "Add it to VanguardLoot with its weight.");
            entry = VanguardLoot.findOrCommon(name);
        }

        collected.add(new CorpseHaul.Drop(entry, amount));
        return true;
    }

    /** Called every client tick, so an unclosed message cannot stay open indefinitely. */
    public void tick() {
        if (!parsing) return;
        if (++ticksOpen > TIMEOUT_TICKS) {
            // Everything collected so far is still real, so this closes rather than
            // discards - a corpse whose separator went missing should still play.
            if (collected.isEmpty()) {
                abandon("nothing arrived within " + TIMEOUT_TICKS + " ticks of the header");
            } else {
                finish("no closing rule arrived within " + TIMEOUT_TICKS + " ticks");
            }
        }
    }

    /** Feed a haul straight in, for the {@code /butcher test vanguard} commands. */
    public void inject(CorpseHaul haul) {
        log.note("§bTest Vanguard corpse §8- " + haul.describe());
        onHaul.accept(haul);
    }

    // ------------------------------------------------------------------ internals

    private void open() {
        sampling = null;
        parsing = true;
        lines = 0;
        afterHeading = 0;
        ticksOpen = 0;
        collected.clear();
    }

    private void finish(String why) {
        if (afterHeading > 0) {
            List<CorpseHaul.Drop> dropped = new ArrayList<>(collected.subList(0, afterHeading));
            collected.subList(0, afterHeading).clear();
            for (CorpseHaul.Drop d : dropped) {
                log.note("§8Not a drop, it was above the heading: \"" + d.name() + "\"");
            }
        }
        CorpseHaul haul = new CorpseHaul(collected);
        parsing = false;
        collected.clear();

        if (haul.isEmpty()) {
            log.note("§cA Vanguard corpse was opened but nothing could be read from it §8(" + why + ")");
            return;
        }

        int rolls = haul.rolls();
        String odd = rolls < VanguardLoot.MIN_ROLLS || rolls > VanguardLoot.MAX_ROLLS
                ? " §c(outside the expected " + VanguardLoot.MIN_ROLLS + "-" + VanguardLoot.MAX_ROLLS + ")" : "";
        log.note("§aVanguard corpse opened §8- " + haul.describe() + odd + " §8(" + why + ")");
        onHaul.accept(haul);
    }

    private void abandon(String why) {
        log.note("§cGave up reading a Vanguard loot message §8- " + why
                + (collected.isEmpty() ? "" : " §8(" + collected.size() + " line(s) discarded)"));
        parsing = false;
        collected.clear();
    }

    /**
     * Drops the " (2)" a chat-compacting mod appends when it folds repeated lines together.
     *
     * Several SkyBlock mods do this, and open two corpses that roll the same way and the
     * whole message comes back collapsed. Without this the header stops matching, so the
     * message is not merely mis-parsed - it is never noticed at all, and the corpse plays
     * no animation. The closing rule and the item counts go the same way.
     *
     * No SkyBlock item name ends in a bracketed number, so this cannot eat a real name.
     */
    private static String decollapse(String text) {
        return COLLAPSED.matcher(text).replaceFirst("");
    }

    /**
     * A sub-heading rather than a drop.
     *
     * "REWARDS" is the only one seen so far, but the test is on the shape rather than the
     * word: a line with letters in it and not one of them lower case. No SkyBlock item is
     * printed that way, and a future heading would be caught without another release.
     */
    private static boolean isHeading(String text) {
        boolean letter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLowerCase(c)) return false;
            letter |= Character.isLetter(c);
        }
        return letter;
    }

    private static String trim(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "...";
    }
}
