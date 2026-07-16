package com.svnhub;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kissweb.UserException;

/** Tests for the merge-request "commits to include" spec parser. */
public class RevSpecTest {

    private static List<long[]> parse(String s) {
        return RevSpec.parse(s, 1, 100);
    }

    private static void assertRanges(List<long[]> got, long[]... expected) {
        assertEquals(expected.length, got.size(), "range count");
        for (int i = 0; i < expected.length; i++)
            assertArrayEquals(expected[i], got.get(i), "range " + i);
    }

    // ---- "all" forms -> null ----

    @Test
    void allForms() {
        assertNull(parse(null));
        assertNull(parse(""));
        assertNull(parse("   "));
        assertNull(parse("all"));
        assertNull(parse("ALL"));
        assertNull(parse(" All "));
        assertNull(parse(" , , "));   // only separators -> all
        assertTrue(RevSpec.isAll("all"));
        assertTrue(RevSpec.isAll(null));
        assertFalse(RevSpec.isAll("5"));
    }

    // ---- single / range / combination ----

    @Test
    void singleRevision() {
        assertRanges(parse("5"), new long[] {5, 5});
    }

    @Test
    void singleRange() {
        assertRanges(parse("3-7"), new long[] {3, 7});
    }

    @Test
    void combination() {
        assertRanges(parse("3-7,10,12-15"),
                new long[] {3, 7}, new long[] {10, 10}, new long[] {12, 15});
    }

    @Test
    void whitespaceAndEmptyTokensTolerated() {
        assertRanges(parse(" 3 , , 5 ,  8-9 "),
                new long[] {3, 3}, new long[] {5, 5}, new long[] {8, 9});
    }

    // ---- normalization: sort + merge overlapping/adjacent ----

    @Test
    void sortsUnordered() {
        assertRanges(parse("10,3-7,12-15"),
                new long[] {3, 7}, new long[] {10, 10}, new long[] {12, 15});
    }

    @Test
    void mergesOverlapping() {
        assertRanges(parse("3-7,5-9"), new long[] {3, 9});
    }

    @Test
    void mergesAdjacent() {
        assertRanges(parse("3-5,6-8"), new long[] {3, 8});
    }

    @Test
    void keepsNonAdjacentSeparate() {
        assertRanges(parse("3-5,7-8"), new long[] {3, 5}, new long[] {7, 8});
    }

    @Test
    void mergesDuplicatesAndContained() {
        assertRanges(parse("5,5,3-9,4-6"), new long[] {3, 9});
    }

    // ---- validation errors ----

    @Test
    void rejectsZeroAndNegative() {
        assertThrows(UserException.class, () -> parse("0"));
        assertThrows(UserException.class, () -> parse("-5"));
    }

    @Test
    void rejectsOutOfRange() {
        assertThrows(UserException.class, () -> RevSpec.parse("150", 1, 100));
        assertThrows(UserException.class, () -> RevSpec.parse("2", 5, 100));   // below minRev
        assertThrows(UserException.class, () -> RevSpec.parse("90-120", 1, 100));
    }

    @Test
    void rejectsBackwardsRange() {
        assertThrows(UserException.class, () -> parse("7-3"));
    }

    @Test
    void rejectsMalformed() {
        assertThrows(UserException.class, () -> parse("abc"));
        assertThrows(UserException.class, () -> parse("3-"));
        assertThrows(UserException.class, () -> parse("3-7-9"));
        assertThrows(UserException.class, () -> parse("3.5"));
        assertThrows(UserException.class, () -> parse("3 5"));
    }

    // ---- boundary values are inclusive ----

    @Test
    void boundariesInclusive() {
        assertRanges(RevSpec.parse("1", 1, 100), new long[] {1, 1});
        assertRanges(RevSpec.parse("100", 1, 100), new long[] {100, 100});
        assertRanges(RevSpec.parse("1-100", 1, 100), new long[] {1, 100});
    }
}
