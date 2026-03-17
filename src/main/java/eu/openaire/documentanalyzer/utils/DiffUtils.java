/*
 * Copyright 2026 OpenAIRE AMKE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
