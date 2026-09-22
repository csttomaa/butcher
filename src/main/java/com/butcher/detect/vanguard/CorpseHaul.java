package com.butcher.detect.vanguard;

import com.butcher.detect.vanguard.VanguardLoot.Entry;
import com.butcher.detect.vanguard.VanguardLoot.Tier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Everything one Vanguard Frozen Corpse gave up.
 *
 * <h2>Why this is not the Scatha's DropResult</h2>
 *
 * A Scatha has one outcome. A corpse has five to eight independent ones, and the whole
 * animation is built on showing them in sequence. Squeezing that into a single-outcome
 * type is what made SkyOcean's slot machine throw away four to seven of every corpse's
 * drops to show one item.
 *
 * <h2>Reveal order is ours to choose</h2>
 *
 * The loot message lists what dropped; nothing says which roll produced what, and nothing
 * requires us to present them in the order Hypixel printed them. So {@link #revealOrder()}
 * sorts worst-first and every corpse becomes a climb that ends on its best item, at no
 * cost and without misrepresenting anything - the haul is exactly what the player got.
 */
public final class CorpseHaul {

    /** One line of the loot message: what it was, how many, and where it sits in the table. */
    public record Drop(Entry entry, int amount) {
        public String name() { return entry.name(); }
        public Tier tier() { return entry.tier(); }
        /** True for a drop whose name is not in {@link VanguardLoot} - see that class. */
        public boolean unknown() { return entry.weight() == 0; }
    }

    private final List<Drop> drops;

    public CorpseHaul(List<Drop> drops) {
        this.drops = List.copyOf(drops);
    }

    public List<Drop> drops() { return drops; }

    /** How many rolls the corpse spent, which is how many strikes the animation plays. */
    public int rolls() { return drops.size(); }

    public boolean isEmpty() { return drops.isEmpty(); }

    /** The best thing in the haul - what the pacing and the final beat are scaled to. */
    public Tier best() {
        Tier best = Tier.COMMON;
        for (Drop d : drops) {
            if (d.tier().ordinal() > best.ordinal()) best = d.tier();
        }
        return best;
    }

    /**
     * True when the whole haul is junk.
     *
     * A corpse never gives literally nothing, so this is what "losing" has to mean - and
     * it is the overwhelming majority of them, which is exactly why it has to be allowed
     * to feel like a loss rather than a quieter win.
     */
    public boolean allCommon() { return best() == Tier.COMMON; }

    /**
     * Whether anything here is worth telling someone about.
     *
     * Deliberately {@code >=} rather than {@code ==}: a haul whose best drop is the
     * Frostbitten Dye is emphatically not "no legendary".
     */
    public boolean hasLegendary() { return best().ordinal() >= Tier.LEGENDARY.ordinal(); }

    /** Whether the Frostbitten Dye is in here. Most players will never see this true. */
    public boolean hasSpecial() { return best() == Tier.SPECIAL; }

    /**
     * The order to reveal them in: worst first, best last.
     *
     * Ties keep the order Hypixel printed, so two commons do not shuffle between runs -
     * the sort is stable and only the tier is compared.
     */
    public List<Drop> revealOrder() {
        List<Drop> ordered = new ArrayList<>(drops);
        ordered.sort(Comparator.comparingInt(d -> d.tier().ordinal()));
        return ordered;
    }

    /** The best drop, or null for an empty haul. Not the same as {@link #best()}, which is a tier. */
    public Drop headline() {
        List<Drop> ordered = revealOrder();
        return ordered.isEmpty() ? null : ordered.get(ordered.size() - 1);
    }

    /** "6 drops, best: Skeleton Key (legendary)" - for the decision log and the test command. */
    public String describe() {
        if (drops.isEmpty()) return "an empty haul";
        Drop head = headline();
        return drops.size() + " drop(s), best: " + head.name()
                + " §8(" + head.tier().name().toLowerCase(java.util.Locale.ROOT) + ")";
    }
}
