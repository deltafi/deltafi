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
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.DeltaFileFlowState;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.KeyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedFlow {
    private String flow;
    private FlowType type;
    private DeltaFileFlowState state;
    private List<String> actions;
    private List<KeyValue> metadata;
    private Boolean metaExactMatch;

    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (StringUtils.isEmpty(flow)) {
            errors.add("ExpectedFlow missing dataSource");
        }

        if (type == null) {
            errors.add("ExpectedFlow missing type");
        }

        if (state == null) {
            state = DeltaFileFlowState.COMPLETE;
        }

        if (actions == null) {
            actions = new ArrayList<>();
        }

        if (metaExactMatch == null) {
            metaExactMatch = false;
        }

        return errors;
    }

    public Map<String, String> metadataToMap() {
        return KeyValueConverter.convertKeyValues(metadata);
    }
}
