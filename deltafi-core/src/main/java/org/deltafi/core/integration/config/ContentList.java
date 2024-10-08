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
package org.deltafi.core.integration.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.FlowType;

import java.util.ArrayList;
import java.util.List;

@Data
public class ContentList {
    private String flow;
    private FlowType type;
    private String action;
    private List<ContentData> data;

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (StringUtils.isEmpty(flow)) {
            errors.add("ContentList missing flow");
        }

        if (type == null) {
            errors.add("ContentList missing type");
        }

        if (StringUtils.isEmpty(action)) {
            errors.add("ContentList missing action");
        }

        if (data == null || data.isEmpty()) {
            errors.add("ContentList missing data");
        } else {
            for (ContentData contentData : data) {
                errors.addAll(contentData.validate());
            }
        }

        return errors;
    }
}
