/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.plugin.deployer;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.deltafi.core.types.Result;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class DeployResult extends Result {
    private List<List<String>> events;
    private String logs;

    public DeployResult() {
        super(true, new ArrayList<>(), new ArrayList<>());
    }

    boolean hasEvents() {
        return events != null && !events.isEmpty();
    }

    public Result detailedResult() {
        Result result = new Result();
        result.setSuccess(this.isSuccess());
        result.getInfo().addAll(this.getInfo());
        result.getErrors().addAll(this.getErrors());

        if (events != null && !events.isEmpty()) {
            // Filter to Warning events and format as simple list
            List<String> warnings = events.stream()
                    .filter(row -> row.size() >= 5 && "Warning".equals(row.get(0)))
                    .map(row -> row.get(1) + ": " + row.get(4)) // Reason: Message
                    .distinct()
                    .toList();

            if (!warnings.isEmpty()) {
                result.getErrors().add(String.join("\n", warnings));
            }
        }

        if (logs != null) {
            result.getErrors().add("Logs:\n" + logs);
        }

        return result;
    }
}
