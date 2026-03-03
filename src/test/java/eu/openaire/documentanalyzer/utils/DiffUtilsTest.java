package eu.openaire.documentanalyzer.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffUtilsTest {

    @Test
    void extractUniquePart_identicalTexts_returnsEmpty() {
        String text = "line one\nline two\nline three";
        String result = DiffUtils.extractUniquePart(text, text);
        assertThat(result).isEmpty();
    }

    @Test
    void extractUniquePart_secondaryHasAdditionalLines_returnsInsertedLines() {
        String main = "line one\nline two";
        String secondary = "line one\nline two\nline three";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).isEqualTo("line three");
    }

    @Test
    void extractUniquePart_secondaryHasChangedLine_returnsChangedLine() {
        String main = "line one\nline two";
        String secondary = "line one\nline modified";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).isEqualTo("line modified");
    }

    @Test
    void extractUniquePart_mainIsEmpty_returnsEntireSecondary() {
        String main = "";
        String secondary = "new content\nmore content";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).isEqualTo("new content\nmore content");
    }

    @Test
    void extractUniquePart_secondaryIsEmpty_returnsEmpty() {
        String main = "line one\nline two";
        String secondary = "";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).isEmpty();
    }

    @Test
    void extractUniquePart_multipleChanges_returnsAllChangedLines() {
        String main = "line one\nline two\nline three";
        String secondary = "line one\nchanged two\nchanged three";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).contains("changed two").contains("changed three");
    }
}
