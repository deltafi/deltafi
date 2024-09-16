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
package org.deltafi.core.plugin.deployer;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.types.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

        if (events != null) {
            StringBuilder eventsMessage = new StringBuilder("Events:\n");

            List<Integer> maxWidths = maxWidths();
            eventsMessage.append(buildRow(K8sEventUtil.EVENT_COLUMNS, maxWidths)).append("\n");
            List<String> separatorRow = K8sEventUtil.EVENT_COLUMNS.stream().map(header -> "-".repeat(header.length())).toList();
            eventsMessage.append(buildRow(separatorRow, maxWidths)).append("\n");
            String rows = events.stream().map(row -> buildRow(row, maxWidths)).collect(Collectors.joining("\n"));
            eventsMessage.append(rows).append("\n\n");

            result.getErrors().add(eventsMessage.toString());
        }

        if (logs != null) {
            result.getErrors().add("Logs:\n" + logs);
        }

        return result;
    }

    private String buildRow(List<String> values, List<Integer> maxWidth) {
        StringBuilder row = new StringBuilder();
        int lastValue = values.size() - 1;
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (i == lastValue) {
                row.append(value);
            } else {
                row.append(StringUtils.rightPad(value, maxWidth.get(i))).append("\t");
            }
        }
        return row.toString();
    }

    private List<Integer> maxWidths() {
        List<Integer> maxWidth = new ArrayList<>(Collections.nCopies(K8sEventUtil.EVENT_COLUMNS.size() - 1, 0));

        for (List<String> eventRow : events) {
            for (int i = 0; i < maxWidth.size(); i++) {
                maxWidth.set(i, Math.max(eventRow.get(i).length(), maxWidth.get(i)));
            }
        }

        return maxWidth;
    }
}
