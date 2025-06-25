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
import org.deltafi.common.types.KeyValue;
import org.deltafi.core.types.OnErrorDataSource;

import java.util.List;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OnErrorDataSourceSnapshot extends DataSourceSnapshot {
    public OnErrorDataSourceSnapshot(String name) {
        super(name);
    }

    public OnErrorDataSourceSnapshot(OnErrorDataSource dataSource) {
        super(dataSource.getName());
        setRunning(dataSource.isRunning());
        setTestMode(dataSource.isTestMode());
        setTopic(dataSource.getTopic());
        setMaxErrors(dataSource.getMaxErrors());
    }
}