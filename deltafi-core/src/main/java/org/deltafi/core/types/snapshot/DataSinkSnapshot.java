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
package org.deltafi.core.types.snapshot;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.core.types.DataSink;

import java.util.HashSet;
import java.util.Set;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DataSinkSnapshot extends FlowSnapshot implements HasExpectedAnnotations {

    private Set<String> expectedAnnotations = new HashSet<>();

    public DataSinkSnapshot(String name) {
        super(name);
    }

    public DataSinkSnapshot(String name, boolean running, boolean testMode) { super(name, running, testMode); }

    public DataSinkSnapshot(DataSink dataSink) {
        this(dataSink.getName());
        setRunning(dataSink.isRunning());
        setTestMode(dataSink.isTestMode());
        setSourcePlugin(dataSink.getSourcePlugin());
        setExpectedAnnotations(dataSink.getExpectedAnnotations());
    }
}
