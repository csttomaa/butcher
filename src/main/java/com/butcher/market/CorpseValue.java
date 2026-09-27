package com.butcher.market;

import com.butcher.detect.vanguard.CorpseHaul;
import com.butcher.detect.vanguard.VanguardLoot;
import com.butcher.detect.vanguard.VanguardLoot.Market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a Vanguard corpse was worth: every drop priced, minus the Skeleton Key it took to open.
 *
 * Bazaar drops are priced at instant-sell and the key at instant-buy (see {@link Bazaar});
 * the Dye, the Locket and the Wisp at their lowest BIN (see {@link LivePrice}). The crystals
 * and the Glacite Powder have no market and count as 0.
 *
 * Asked for when the loot message is read ({@link #request}) and worked out again every frame
 * the window is open ({@link #of}), so a price that arrives late fills itself in.
 */
public final class CorpseValue {
    private CorpseValue() {}

    public static final String KEY_ID = "SKELETON_KEY";

    /** Where one line's price came from. */
    public enum Source {
        BAZAAR, AUCTION,
        /** Cannot be sold. Worth 0. */
        UNSELLABLE,
        /** Not priced: an item this table does not know, or the site had no answer. */
        UNKNOWN,
        /** Still being fetched. */
        PENDING
    }

    /** One drop, priced. {@code unit} and {@code total} are 0 unless the source has a price. */
    public record Line(CorpseHaul.Drop drop, Source source, double unit, long total) {}

    /**
     * The whole corpse.
     *
     * @param items everything the drops are worth together
     * @param key   what the Skeleton Key costs, or -1 if not known yet
     * @param net   items minus key; only meaningful when {@code complete}
     */
    public record Result(List<Line> lines, long items, Source keySource, long key, long net, boolean complete) {}

    /** Asks for every price this corpse will need. Cheap to call twice. */
    public static void request(CorpseHaul haul) {
        Bazaar.request();
        for (CorpseHaul.Drop d : haul.drops()) {
            VanguardLoot.Listing l = VanguardLoot.listing(d.name());
            if (l != null && l.market() == Market.AUCTION) LivePrice.request(l.id());
        }
    }

    /** Prices the corpse with whatever has arrived so far. Most valuable line first. */
    public static Result of(CorpseHaul haul) {
        List<Line> lines = new ArrayList<>();
        long items = 0;
        boolean complete = true;
        for (CorpseHaul.Drop d : haul.drops()) {
            Line line = price(d);
            if (line.source() == Source.PENDING) complete = false;
            items += line.total();
            lines.add(line);
        }
        lines.sort(Comparator.comparingLong(Line::total).reversed());

        Double key = Bazaar.buy(KEY_ID);
        Source keySource = key != null ? Source.BAZAAR : Bazaar.pending() ? Source.PENDING : Source.UNKNOWN;
        if (keySource == Source.PENDING) complete = false;
        long keyCost = key == null ? -1 : Math.round(key);
        long net = items - Math.max(0, keyCost);
        return new Result(lines, items, keySource, keyCost, net, complete);
    }

    private static Line price(CorpseHaul.Drop d) {
        VanguardLoot.Listing l = VanguardLoot.listing(d.name());
        if (l == null) return new Line(d, Source.UNKNOWN, 0, 0);
        switch (l.market()) {
            case NONE:
                return new Line(d, Source.UNSELLABLE, 0, 0);
            case BAZAAR: {
                Double unit = Bazaar.sell(l.id());
                if (unit != null) return new Line(d, Source.BAZAAR, unit, Math.round(unit * d.amount()));
                return new Line(d, Bazaar.pending() ? Source.PENDING : Source.UNKNOWN, 0, 0);
            }
            default: {
                Long unit = LivePrice.cached(l.id());
                if (unit != null) return new Line(d, Source.AUCTION, unit, unit * d.amount());
                return new Line(d, LivePrice.pending(l.id()) ? Source.PENDING : Source.UNKNOWN, 0, 0);
            }
        }
    }

    /** Coins the short way: 950, 12.4k, 1.42M, 504.9M, 1.23B. */
    public static String coins(long c) {
        long a = Math.abs(c);
        String s;
        if (a >= 1_000_000_000L) s = String.format("%.2fB", a / 1e9);
        else if (a >= 100_000_000L) s = String.format("%.1fM", a / 1e6);
        else if (a >= 1_000_000L) s = String.format("%.2fM", a / 1e6);
        else if (a >= 10_000L) s = String.format("%.1fk", a / 1e3);
        else s = String.format("%,d", a);
        return c < 0 ? "-" + s : s;
    }
}
