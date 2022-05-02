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
package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.api.types.DeleteActionSchema;

import java.util.Collections;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class DeleteActionConfiguration extends org.deltafi.core.domain.generated.types.DeleteActionConfiguration implements ActionConfiguration {

    public DeleteActionConfiguration(String name, String type) {
        super(name, null, type, Collections.emptyMap());
    } 

    @Override
    public List<String> validate(ActionSchema actionSchema) {
        if (!(actionSchema instanceof DeleteActionSchema)) {
            return List.of("Action: " + getType() + " is not registered as a DeleteAction");
        }
        return Collections.emptyList();
    }
}