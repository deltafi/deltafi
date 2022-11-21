/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.test.action;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.deltafi.common.types.KeyValue;

import java.util.Collections;
import java.util.List;

@Data
@SuperBuilder
public class IOContent {

    String name;

    String contentType;

    @Builder.Default
    List<KeyValue> metadata = Collections.emptyList();

}
