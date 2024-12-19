/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.common.types.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.common.types.KeyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedDeltaFile {
    private DeltaFileStage stage;
    private Integer childCount;
    private Integer parentCount;
    private List<ExpectedFlow> expectedFlows;
    private List<ExpectedDeltaFile> children;
    private ExpectedContentList expectedContent;
    private List<KeyValue> annotations;

    public List<String> validate(int level) {
        List<String> errors = new ArrayList<>();

        if (stage == null) {
            errors.add("Missing stage in expectedDeltaFile");
        }

        if (children == null) {
            children = new ArrayList<>();
        }

        if (expectedFlows == null) {
            expectedFlows = new ArrayList<>();
        }

        if (childCount == null || childCount == 0) {
            childCount = children.size();
        }

        if (children.size() > childCount) {
            errors.add("Size of children (" + children.size() + ") exceeds childCount ( " + childCount + ")");
        }

        int pos = 0;
        for (ExpectedDeltaFile child : children) {
            List<String> childErrors = child.validate(level + 1);
            if (!childErrors.isEmpty()) {
                errors.add("Child " + level + ":" + pos + " invalid");
                errors.addAll(childErrors);
            }
            ++pos;
        }

        for (ExpectedFlow ef : expectedFlows) {
            errors.addAll(ef.validate());
        }

        if (expectedContent != null) {
            errors.addAll(expectedContent.validate());
        }
        return errors;
    }

    public Map<String, String> annotationsToMap() {
        return KeyValueConverter.convertKeyValues(annotations);
    }
}
