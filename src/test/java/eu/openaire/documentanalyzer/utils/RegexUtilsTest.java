package eu.openaire.documentanalyzer.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegexUtilsTest {

    @Test
    void countOccurrences_multipleMatches_returnsCorrectCount() {
        assertThat(RegexUtils.countOccurrences("hello world hello", "hello")).isEqualTo(2);
    }

    @Test
    void countOccurrences_noMatches_returnsZero() {
        assertThat(RegexUtils.countOccurrences("hello world", "xyz")).isZero();
    }

    @Test
    void countOccurrences_regexPattern_returnsCorrectCount() {
        assertThat(RegexUtils.countOccurrences("cat bat rat", "[cbr]at")).isEqualTo(3);
    }

    @Test
    void countOccurrences_emptyInput_returnsZero() {
        assertThat(RegexUtils.countOccurrences("", "pattern")).isZero();
    }

    @Test
    void countOccurrences_singleMatch_returnsOne() {
        assertThat(RegexUtils.countOccurrences("one two three", "two")).isEqualTo(1);
    }

    @Test
    void countOccurrences_overlappingMatches_countsNonOverlapping() {
        // "aa" in "aaaa" matches at positions 0 and 2 (non-overlapping)
        assertThat(RegexUtils.countOccurrences("aaaa", "aa")).isEqualTo(2);
    }
}
