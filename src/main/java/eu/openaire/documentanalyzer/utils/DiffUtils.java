package eu.openaire.documentanalyzer.utils;

import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DiffUtils {


    public static String extractUniquePart(String mainText, String secondaryText) {
        List<String> mainTextLines = Arrays.asList(mainText.split("\n"));
        List<String> secondaryTextLines = Arrays.asList(secondaryText.split("\n"));

        Patch<String> patch = com.github.difflib.DiffUtils.diff(mainTextLines, secondaryTextLines);

        // collect only the lines that changed
        return patch.getDeltas().stream()
                .filter(delta -> delta.getType() == DeltaType.CHANGE
                        || delta.getType() == DeltaType.INSERT)
                .flatMap(delta -> delta.getTarget().getLines().stream())
                .collect(Collectors.joining("\n"));
    }

    private DiffUtils() {
    }
}
