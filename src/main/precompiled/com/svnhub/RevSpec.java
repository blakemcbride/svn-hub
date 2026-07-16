package com.svnhub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.kissweb.UserException;

/**
 * Parser for a merge request's "commits to include" specification.
 *
 * <p>A spec selects which <b>source revisions</b> a merge request applies.  It is
 * one of:</p>
 * <ul>
 *   <li>{@code all} (or empty/null) — every eligible revision; {@link #parse} returns {@code null}</li>
 *   <li>{@code N} — the single revision {@code N} (the change committed in rN)</li>
 *   <li>{@code N-M} — revisions {@code N} through {@code M} inclusive</li>
 *   <li>any comma-separated combination, e.g. {@code 3-7,10,12-15}</li>
 * </ul>
 *
 * <p>{@link #parse} returns a normalized, sorted list of <b>inclusive</b>
 * {@code [from,to]} revision pairs (overlapping/adjacent pairs merged), or
 * {@code null} for "all".  Every revision is validated to lie within
 * {@code [minRev, maxRev]}; malformed or out-of-range input throws
 * {@link UserException} (a message suitable for showing to the user).</p>
 *
 * <p>SVN merge semantics: to apply the changes in inclusive revisions
 * {@code from..to} the caller uses the range {@code (from-1, to]} — see
 * {@link SvnRepo}.</p>
 */
public final class RevSpec {

    private RevSpec() {
    }

    /** True if the spec means "all revisions" (null, empty, or the word "all"). */
    public static boolean isAll(String spec) {
        if (spec == null)
            return true;
        String s = spec.trim();
        return s.isEmpty() || s.equalsIgnoreCase("all");
    }

    /**
     * Parse a spec into normalized inclusive {@code [from,to]} ranges, or
     * {@code null} for "all".
     *
     * @param spec   the user-entered specification
     * @param minRev smallest selectable revision (inclusive)
     * @param maxRev largest selectable revision (inclusive)
     * @throws UserException on malformed input or a revision outside [minRev, maxRev]
     */
    public static List<long[]> parse(String spec, long minRev, long maxRev) {
        if (isAll(spec))
            return null;
        List<long[]> ranges = new ArrayList<>();
        for (String rawTok : spec.split(",")) {
            String tok = rawTok.trim();
            if (tok.isEmpty())
                continue;   // tolerate "3,,5" and trailing commas
            long from;
            long to;
            int dash = tok.indexOf('-');
            if (dash == 0)
                throw badToken(tok);   // "-5" (no negative revisions)
            else if (dash > 0) {
                from = parseRev(tok.substring(0, dash).trim(), tok);
                to = parseRev(tok.substring(dash + 1).trim(), tok);
            } else {
                from = parseRev(tok, tok);
                to = from;
            }
            if (from > to)
                throw new UserException("Invalid range '" + tok + "': the start revision is after the end.");
            if (from < minRev || to > maxRev)
                throw new UserException("Revision '" + tok + "' is out of range. Valid revisions are "
                        + minRev + "-" + maxRev + ".");
            ranges.add(new long[] {from, to});
        }
        if (ranges.isEmpty())
            return null;   // spec was only commas/whitespace -> treat as "all"
        return normalize(ranges);
    }

    private static long parseRev(String s, String tok) {
        long v;
        try {
            v = Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw badToken(tok);
        }
        if (v < 1)
            throw badToken(tok);
        return v;
    }

    private static UserException badToken(String tok) {
        return new UserException("Invalid revision '" + tok
                + "'. Use a revision number, a range N-M, 'all', or a comma-separated combination (e.g. 3-7,10,12-15).");
    }

    /** Sort by start, then merge overlapping or contiguous ranges into a minimal set. */
    private static List<long[]> normalize(List<long[]> in) {
        in.sort(Comparator.comparingLong((long[] r) -> r[0]).thenComparingLong(r -> r[1]));
        List<long[]> out = new ArrayList<>();
        for (long[] r : in) {
            if (!out.isEmpty()) {
                long[] last = out.get(out.size() - 1);
                if (r[0] <= last[1] + 1) {          // overlapping or directly adjacent
                    if (r[1] > last[1])
                        last[1] = r[1];
                    continue;
                }
            }
            out.add(new long[] {r[0], r[1]});
        }
        return out;
    }
}
